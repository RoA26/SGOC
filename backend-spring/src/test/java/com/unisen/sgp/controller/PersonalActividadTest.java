package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.support.VentasApiTest;
import com.unisen.sgp.tenant.TenantFilter;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * B.6: vista de personal del administrador. Solo trabajadores de su empresa, con la última
 * operación registrada (nunca una "última conexión") y los conteos de los últimos 30 días.
 */
class PersonalActividadTest extends VentasApiTest {

    private static final String PERSONAL = "/api/v1/trabajadores/actividad";

    private long proveedor;
    private long producto;
    private long comprasId;

    @BeforeEach
    void catalogo() throws Exception {
        proveedor = crearProveedor("900111111-1", "Textiles del Valle");
        producto = productoALaVenta(proveedor, "CAM-01", "10000", "GRAVADO", "19", 10, "7000");
        comprasId = contar("SELECT id FROM usuarios WHERE username = 'compras'");
    }

    private void solicitar(String token) throws Exception {
        postJson("/api/v1/solicitudes", token, json(Map.of("justificacion", "Reposición para la bodega principal.",
                "detalles", List.of(Map.of("productoId", producto, "cantidad", 2))))).andExpect(status().isCreated());
    }

    private JsonNode fila(JsonNode pagina, String username) {
        for (JsonNode fila : pagina.get("content")) {
            if (fila.get("username").asText().equals(username)) {
                return fila;
            }
        }
        throw new AssertionError("No aparece " + username);
    }

    @Test
    void sinOperacionesNoHayUltimaOperacionNiSeExponeElCorreo() throws Exception {
        JsonNode compras = fila(leer(getJson(PERSONAL, adminToken).andExpect(status().isOk())), "compras");

        assertThat(compras.get("estado").asText()).isEqualTo("ACTIVO");
        assertThat(compras.has("email")).isFalse();
        JsonNode actividad = compras.get("actividad");
        assertThat(actividad.has("ultimaOperacion")).isFalse(); // los nulos se omiten
        assertThat(actividad.get("dias").asInt()).isEqualTo(30);
        assertThat(actividad.get("solicitudes").asLong()).isZero();
        assertThat(actividad.get("recepciones").asLong()).isZero();
        assertThat(actividad.get("ventas").asLong()).isZero();
    }

    @Test
    void cuentaSoloLoQueFirmoElTrabajadorYTomaLaOperacionMasReciente() throws Exception {
        solicitar(usuarioToken);
        solicitar(usuarioToken);
        // Una solicitud de hace 60 días: cuenta para la historia, no para los últimos 30 días.
        jdbcTemplate.update("UPDATE solicitudes SET fecha = ? WHERE id = (SELECT MIN(id) FROM solicitudes)",
                Timestamp.from(Instant.now().minus(Duration.ofDays(60))));
        JsonNode orden = ordenEmitida(proveedor, producto, 5);
        recibir(orden.get("id").asLong(), usuarioToken, new long[] {orden.at("/detalles/0/id").asLong(), 2})
                .andExpect(status().isCreated());
        venderOk(usuarioToken, lineas(linea(producto, 1)));
        // Operaciones del administrador: no se atribuyen al trabajador.
        solicitar(adminToken);
        venderOk(adminToken, lineas(linea(producto, 1)));

        JsonNode pagina = leer(getJson(PERSONAL, adminToken).andExpect(status().isOk()));
        assertThat(pagina.get("content")).hasSize(1); // el administrador no es personal
        JsonNode actividad = fila(pagina, "compras").get("actividad");
        assertThat(actividad.get("solicitudes").asLong()).isEqualTo(1);
        assertThat(actividad.get("recepciones").asLong()).isEqualTo(1);
        assertThat(actividad.get("ventas").asLong()).isEqualTo(1);
        assertThat(actividad.at("/ultimaOperacion/tipo").asText()).isEqualTo("VENTA");

        Instant vendidaEn = jdbcTemplate.queryForObject(
                "SELECT vendida_en FROM ventas WHERE vendida_por_id = ?", Timestamp.class, comprasId).toInstant();
        assertThat(Instant.parse(actividad.at("/ultimaOperacion/en").asText())).isEqualTo(vendidaEn);
    }

    @Test
    void cadaAdministradorVeSoloASuPersonalYSuActividad() throws Exception {
        solicitar(usuarioToken);
        Empresa norte = crearEmpresa("Ferretería Norte", "900000002-2");
        String gerenteNorte = crearUsuarioYEntrar("gerente.norte", Rol.ADMINISTRADOR_COMERCIAL, norte);
        crearUsuarioYEntrar("operario.norte", Rol.TRABAJADOR, norte);

        getJson(PERSONAL, gerenteNorte)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("operario.norte"))
                .andExpect(jsonPath("$.content[0].actividad.ultimaOperacion").doesNotExist());

        // La cabecera X-Tenant-ID no cambia la empresa de un administrador.
        mockMvc.perform(get(PERSONAL).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .header(TenantFilter.HEADER_TENANT, norte.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("compras"))
                .andExpect(jsonPath("$.content[0].actividad.solicitudes").value(1));
    }

    @Test
    void filtraPorEstadoComoElListadoDeTrabajadores() throws Exception {
        crearUsuarioYEntrar("operario.dos", Rol.TRABAJADOR, empresa);
        jdbcTemplate.update("UPDATE usuarios SET estado = 'INACTIVO', activo = FALSE WHERE username = 'operario.dos'");

        getJson(PERSONAL + "?estado=INACTIVO", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("operario.dos"))
                .andExpect(jsonPath("$.content[0].estado").value("INACTIVO"));
    }

    @Test
    void elTrabajadorNoLoVeYElSuperAdminSoloSupervisandoUnaEmpresa() throws Exception {
        getJson(PERSONAL, usuarioToken).andExpect(status().isForbidden());

        String root = crearUsuarioYEntrar("root", Rol.SUPER_ADMIN, null);
        getGlobal(PERSONAL, root).andExpect(status().isBadRequest());
        getEnEmpresa(PERSONAL, root, empresa.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("compras"));
    }
}
