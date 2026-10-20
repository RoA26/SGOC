package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.support.VentasApiTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/**
 * Gestión comercial: el administrador comercial cambia precios e IVA de su empresa con historial;
 * el trabajador, el SUPER_ADMIN y otras empresas no pueden; un token antiguo recibe los permisos
 * vigentes en la BD; la rentabilidad unitaria distingue lo desconocido de lo nulo.
 */
class GestionComercialTest extends VentasApiTest {

    @Value("${app.jwt.secret}")
    private String secreto;
    @Value("${app.jwt.issuer}")
    private String emisor;

    private long proveedor;
    private long producto;

    @BeforeEach
    void prepararCatalogo() throws Exception {
        proveedor = crearProveedor("900111111-1", "Aceros Andinos");
        producto = productoALaVenta(proveedor, "TOR-01", "1000", "GRAVADO", "19", 10, "750");
    }

    @Test
    void elAdministradorComercialCambiaElPrecioYQuedaEnElHistorial() throws Exception {
        JsonNode respuesta = leer(condiciones(adminToken, producto, "1200", "GRAVADO", "19", "Ajuste por inflación")
                .andExpect(status().isOk()));
        assertThat(respuesta.get("precioVenta").decimalValue()).isEqualByComparingTo("1200");
        assertThat(respuesta.get("precioConIva").decimalValue()).isEqualByComparingTo("1428");
        assertThat(respuesta.get("costoPromedio").decimalValue()).isEqualByComparingTo("750");
        assertThat(respuesta.get("utilidadUnitaria").decimalValue()).isEqualByComparingTo("450");
        assertThat(respuesta.get("margen").decimalValue()).isEqualByComparingTo("37.5");
        assertThat(respuesta.get("recargo").decimalValue()).isEqualByComparingTo("60");
        assertThat(respuesta.get("bajoCosto").asBoolean()).isFalse();

        getJson(COMERCIAL + "/historial-precios?productoId=" + producto, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].precioAnterior").value(1000))
                .andExpect(jsonPath("$.content[0].precioNuevo").value(1200))
                .andExpect(jsonPath("$.content[0].motivo").value("Ajuste por inflación"))
                .andExpect(jsonPath("$.content[0].usuario.username").value("admin"))
                .andExpect(jsonPath("$.content[0].costoReferencia").value(750))
                .andExpect(jsonPath("$.content[1].precioAnterior").doesNotExist())
                .andExpect(jsonPath("$.content[1].precioNuevo").value(1000));

        // Repetir los mismos valores no registra un cambio nuevo (idempotente).
        condiciones(adminToken, producto, "1200", "GRAVADO", "19", "Otra vez").andExpect(status().isOk());
        assertThat(contar("SELECT COUNT(*) FROM cambios_precio WHERE producto_id = ?", producto)).isEqualTo(2);
    }

    @Test
    void unPrecioBajoElCostoSePermiteYSeAdvierte() throws Exception {
        JsonNode respuesta = leer(condiciones(adminToken, producto, "700", "GRAVADO", "19", "Liquidación")
                .andExpect(status().isOk()));
        assertThat(respuesta.get("bajoCosto").asBoolean()).isTrue();
        assertThat(respuesta.get("utilidadUnitaria").decimalValue()).isEqualByComparingTo("-50");
        getJson(COMERCIAL + "/productos?filtro=BAJO_COSTO", adminToken)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("TOR-01"));
    }

    @Test
    void costoDesconocidoNoSePresentaComoCero() throws Exception {
        long sinCosto = productoALaVenta(proveedor, "SIN-01", "500", "EXCLUIDO", "0", 5, null);
        getJson(COMERCIAL + "/productos/" + sinCosto, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costoPromedio").doesNotExist())
                .andExpect(jsonPath("$.utilidadUnitaria").doesNotExist())
                .andExpect(jsonPath("$.margen").doesNotExist())
                .andExpect(jsonPath("$.recargo").doesNotExist())
                .andExpect(jsonPath("$.bajoCosto").value(false));
        getJson(COMERCIAL + "/productos?filtro=SIN_COSTO", adminToken).andExpect(jsonPath("$.content[0].sku").value("SIN-01"));
    }

    @Test
    void elTratamientoYLaTarifaDelIvaDebenSerCoherentes() throws Exception {
        condiciones(adminToken, producto, "1000", "GRAVADO", "0", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.tarifaIva").exists());
        condiciones(adminToken, producto, "1000", "EXENTO", "19", null).andExpect(status().isBadRequest());
        condiciones(adminToken, producto, "1000", "EXCLUIDO", "5", null).andExpect(status().isBadRequest());
        condiciones(adminToken, producto, "0", "GRAVADO", "19", null).andExpect(status().isBadRequest());
        condiciones(adminToken, producto, "-5", "GRAVADO", "19", null).andExpect(status().isBadRequest());
        condiciones(adminToken, producto, "1000", "EXENTO", "0", "Exento de demostración").andExpect(status().isOk())
                .andExpect(jsonPath("$.tratamientoIva").value("EXENTO"))
                .andExpect(jsonPath("$.precioConIva").value(1000));
    }

    @Test
    void elTrabajadorNoCambiaPreciosNiVeCostos() throws Exception {
        condiciones(usuarioToken, producto, "1", "EXCLUIDO", "0", "Manipulado").andExpect(status().isForbidden());
        putJson(COMERCIAL + "/politica", usuarioToken, json(Map.of("descuentoMaxTrabajador", 100,
                "descuentoMaxAdministrador", 100, "ventaBajoCosto", "PERMITIR"))).andExpect(status().isForbidden());
        postJson(COMERCIAL + "/autorizaciones", usuarioToken, json(Map.of("descuentoMaximo", 50, "permiteBajoCosto", true,
                "motivo", "Me lo autorizo"))).andExpect(status().isForbidden());
        getJson(COMERCIAL + "/productos", usuarioToken).andExpect(status().isForbidden());
        getJson(COMERCIAL + "/historial-precios", usuarioToken).andExpect(status().isForbidden());
        // El precio sigue igual.
        getJson(COMERCIAL + "/productos/" + producto, adminToken).andExpect(jsonPath("$.precioVenta").value(1000));
        // En la caja ve el precio vigente y las existencias, pero no el costo.
        getJson(VENTAS + "/productos?q=TOR", usuarioToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].precioVenta").value(1000))
                .andExpect(jsonPath("$.content[0].existencias").value(10))
                .andExpect(jsonPath("$.content[0].costoPromedio").doesNotExist());
    }

    @Test
    void otraEmpresaNoGestionaLosPreciosAjenosYElSuperAdminNoTieneAutorizacionComercial() throws Exception {
        Empresa norte = crearEmpresa("Norte", "900000002-2");
        String adminNorte = crearUsuarioYEntrar("nora", Rol.ADMINISTRADOR_COMERCIAL, norte);
        condiciones(adminNorte, producto, "1", "EXCLUIDO", "0", "Ajeno").andExpect(status().isNotFound());
        getJson(COMERCIAL + "/productos/" + producto, adminNorte).andExpect(status().isNotFound());
        getJson(COMERCIAL + "/productos", adminNorte).andExpect(jsonPath("$.content.length()").value(0));
        getJson(COMERCIAL + "/historial-precios?productoId=" + producto, adminNorte).andExpect(status().isNotFound());

        String superAdmin = crearUsuarioYEntrar("root", Rol.SUPER_ADMIN, null);
        // Supervisa (lectura) pero no cambia precios: no hay autorización comercial implícita.
        getEnEmpresa(COMERCIAL + "/productos/" + producto, superAdmin, empresa.getId()).andExpect(status().isOk());
        // En el modo global no se listan costos ni precios de todas las empresas.
        getGlobal(COMERCIAL + "/productos", superAdmin).andExpect(status().isBadRequest());
        getGlobal(COMERCIAL + "/politica", superAdmin).andExpect(status().isBadRequest());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put(COMERCIAL + "/productos/" + producto + "/condiciones")
                        .header("Authorization", "Bearer " + superAdmin)
                        .header("X-Tenant-ID", empresa.getId().toString())
                        .contentType("application/json")
                        .content(json(Map.of("precioVenta", 1, "tratamientoIva", "EXCLUIDO", "tarifaIva", 0))))
                .andExpect(status().isForbidden());
        assertThat(contar("SELECT COUNT(*) FROM cambios_precio WHERE producto_id = ?", producto)).isEqualTo(1);
    }

    @Test
    void unTokenAntiguoRecibeLosPermisosDeLaBaseDeDatos() throws Exception {
        long adminId = jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE username = 'admin'", Long.class);
        long trabajadorId = jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE username = 'compras'", Long.class);
        // Token emitido antes de V14, con el rol antiguo en el claim: sigue valiendo con el rol nuevo.
        String antiguo = token("admin", adminId, "ADMIN_EMPRESA");
        condiciones(antiguo, producto, "1300", "GRAVADO", "19", "Con token antiguo").andExpect(status().isOk());
        // Un claim manipulado no eleva permisos: el trabajador sigue siendo trabajador en la BD.
        String elevado = token("compras", trabajadorId, "ADMINISTRADOR_COMERCIAL");
        condiciones(elevado, producto, "1", "EXCLUIDO", "0", "Elevado").andExpect(status().isForbidden());
        getJson("/api/auth/me", antiguo).andExpect(jsonPath("$.rol").value("ADMINISTRADOR_COMERCIAL"));
    }

    @Test
    void laPoliticaYLasAutorizacionesSoloLasDefineElAdministradorComercial() throws Exception {
        getJson(COMERCIAL + "/politica", adminToken).andExpect(jsonPath("$.porDefecto").value(true))
                .andExpect(jsonPath("$.descuentoMaxTrabajador").value(0));
        politica("10", "30", "AUTORIZACION").andExpect(status().isOk())
                .andExpect(jsonPath("$.porDefecto").value(false))
                .andExpect(jsonPath("$.actualizadaPor.username").value("admin"));
        politica("40", "30", "PERMITIR").andExpect(status().isBadRequest());

        JsonNode autorizacion = leer(postJson(COMERCIAL + "/autorizaciones", adminToken, json(Map.of("descuentoMaximo", 25,
                "permiteBajoCosto", false, "motivo", "Cliente mayorista"))).andExpect(status().isCreated()));
        assertThat(autorizacion.get("codigo").asText()).matches("[A-Z2-9]{4}-[A-Z2-9]{4}");
        assertThat(autorizacion.get("estado").asText()).isEqualTo("VIGENTE");
        // Por encima de su propio límite (30 %): no.
        postJson(COMERCIAL + "/autorizaciones", adminToken, json(Map.of("descuentoMaximo", 31, "permiteBajoCosto", false,
                "motivo", "Demasiado"))).andExpect(status().isBadRequest());
        // El listado no expone el código completo.
        getJson(COMERCIAL + "/autorizaciones", adminToken)
                .andExpect(jsonPath("$.content[0].codigo").value("…" + autorizacion.get("codigo").asText().substring(5)));
    }

    private String token(String username, long id, String rol) {
        Instant ahora = Instant.now();
        return Jwts.builder().subject(username).issuer(emisor).issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(600)))
                .claim("uid", id).claim("rol", rol).claim("empresaId", empresa.getId())
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secreto)))
                .compact();
    }
}
