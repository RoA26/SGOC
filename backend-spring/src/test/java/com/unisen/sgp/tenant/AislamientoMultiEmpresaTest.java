package com.unisen.sgp.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Proveedor;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.repository.ProveedorRepository;
import com.unisen.sgp.security.JwtUtil;
import com.unisen.sgp.support.ApiIntegrationTest;
import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Aislamiento entre empresas (tenants): "Unisen" ({@code empresa}, con adminToken y
 * usuarioToken de la clase base) frente a "Ferretería Norte" ({@code norte}), y el
 * SUPER_ADMIN en modo global y con {@code X-Tenant-ID}.
 */
class AislamientoMultiEmpresaTest extends ApiIntegrationTest {

    private static final String PROVEEDORES = "/api/v1/proveedores";
    private static final String PRODUCTOS = "/api/v1/productos";
    private static final String SOLICITUDES = "/api/v1/solicitudes";

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private ProveedorRepository proveedorRepository;

    private Empresa norte;
    private String gerenteNorte;
    private String usuarioNorte;
    private String superAdmin;

    @BeforeEach
    void crearSegundaEmpresaYSuperAdmin() throws Exception {
        norte = crearEmpresa("Ferretería Norte", "900000002-2");
        gerenteNorte = crearUsuarioYEntrar("gerente.norte", Rol.GERENTE, norte);
        usuarioNorte = crearUsuarioYEntrar("compras.norte", Rol.USUARIO, norte);
        superAdmin = crearUsuarioYEntrar("plataforma", Rol.SUPER_ADMIN, null);
    }

    // ------------------------------------------------------------ helpers

    private ResultActions enviar(MockHttpServletRequestBuilder peticion, String token, Long tenant) throws Exception {
        peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (tenant != null) {
            peticion.header(TenantFilter.HEADER_TENANT, tenant.toString());
        }
        return mockMvc.perform(peticion);
    }

    private ResultActions postComo(String url, String token, Long tenant, String body) throws Exception {
        return enviar(post(url).contentType(MediaType.APPLICATION_JSON).content(body), token, tenant);
    }

    private static String proveedor(String nit, String razonSocial) {
        return """
                {"nit": "%s", "razonSocial": "%s", "email": "ventas@prov.com"}
                """.formatted(nit, razonSocial);
    }

    private static String producto(String sku, long proveedorId) {
        return """
                {"sku": "%s", "nombre": "Producto %s", "precio": 1000, "proveedorId": %d}
                """.formatted(sku, sku, proveedorId);
    }

    private static String solicitud(long productoId) {
        return """
                {"justificacion": "Material para el taller de mantenimiento.",
                 "detalles": [{"productoId": %d, "cantidad": 2}]}
                """.formatted(productoId);
    }

    private long crear(String url, String token, String body) throws Exception {
        return leer(postJson(url, token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private long empresaDe(String tabla, long id) {
        return jdbcTemplate.queryForObject("SELECT empresa_id FROM " + tabla + " WHERE id = ?", Long.class, id);
    }

    // ---------------------------------------------------------- catálogos

    @Test
    void cadaEmpresaSoloVeYModificaSusCatalogos() throws Exception {
        long proveedorUnisen = crear(PROVEEDORES, adminToken, proveedor("900123456-7", "Aceros Andinos"));
        long productoUnisen = crear(PRODUCTOS, adminToken, producto("TOR-001", proveedorUnisen));

        // La empresa la fija el contexto, no el cliente.
        assertThat(empresaDe("proveedores", proveedorUnisen)).isEqualTo(empresa.getId());
        assertThat(empresaDe("productos", productoUnisen)).isEqualTo(empresa.getId());

        getJson(PROVEEDORES, gerenteNorte).andExpect(jsonPath("$.page.totalElements").value(0));
        getJson(PRODUCTOS, usuarioNorte).andExpect(jsonPath("$.page.totalElements").value(0));
        // Por id tampoco: para Norte, los datos de Unisen no existen.
        getJson(PROVEEDORES + "/" + proveedorUnisen, gerenteNorte).andExpect(status().isNotFound());
        getJson(PRODUCTOS + "/" + productoUnisen, gerenteNorte).andExpect(status().isNotFound());
        putJson(PROVEEDORES + "/" + proveedorUnisen, gerenteNorte, proveedor("900123456-7", "Hackeado"))
                .andExpect(status().isNotFound());
        deleteJson(PRODUCTOS + "/" + productoUnisen, gerenteNorte).andExpect(status().isNotFound());

        getJson(PROVEEDORES + "/" + proveedorUnisen, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razonSocial").value("Aceros Andinos"));
    }

    @Test
    void nitYSkuSonUnicosPorEmpresaNoEnTodaLaPlataforma() throws Exception {
        long proveedorUnisen = crear(PROVEEDORES, adminToken, proveedor("900123456-7", "Aceros Andinos"));
        long proveedorNorte = crear(PROVEEDORES, gerenteNorte, proveedor("900123456-7", "Aceros Andinos"));
        crear(PRODUCTOS, adminToken, producto("TOR-001", proveedorUnisen));
        crear(PRODUCTOS, gerenteNorte, producto("TOR-001", proveedorNorte));

        postJson(PROVEEDORES, adminToken, proveedor("900123456-7", "Repetido"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.nit").exists());
        postJson(PRODUCTOS, gerenteNorte, producto("TOR-001", proveedorNorte))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.sku").exists());
    }

    @Test
    void noSePuedenReferenciarDatosDeOtraEmpresa() throws Exception {
        long proveedorUnisen = crear(PROVEEDORES, adminToken, proveedor("900123456-7", "Aceros Andinos"));
        long productoUnisen = crear(PRODUCTOS, adminToken, producto("TOR-001", proveedorUnisen));

        postJson(PRODUCTOS, gerenteNorte, producto("ROB-001", proveedorUnisen))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.proveedorId").exists());
        postJson(SOLICITUDES, usuarioNorte, solicitud(productoUnisen))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['detalles[0].productoId']").value("El producto no existe o fue dado de baja."));
    }

    // --------------------------------------------------------- solicitudes

    @Test
    void lasSolicitudesNoSeVenNiSeRevisanEntreEmpresas() throws Exception {
        long proveedorUnisen = crear(PROVEEDORES, adminToken, proveedor("900123456-7", "Aceros Andinos"));
        long productoUnisen = crear(PRODUCTOS, adminToken, producto("TOR-001", proveedorUnisen));
        long solicitudUnisen = crear(SOLICITUDES, usuarioToken, solicitud(productoUnisen));
        assertThat(empresaDe("solicitudes", solicitudUnisen)).isEqualTo(empresa.getId());

        getJson(SOLICITUDES, gerenteNorte).andExpect(jsonPath("$.page.totalElements").value(0));
        getJson(SOLICITUDES + "/" + solicitudUnisen, gerenteNorte).andExpect(status().isNotFound());
        patchJson(SOLICITUDES + "/" + solicitudUnisen + "/estado", gerenteNorte, "{\"estado\": \"APROBADA\"}")
                .andExpect(status().isNotFound());

        getJson(SOLICITUDES, adminToken).andExpect(jsonPath("$.page.totalElements").value(1));
    }

    // ------------------------------------------------------ cabecera tenant

    @Test
    void unGerenteNoPuedeCambiarDeEmpresaConLaCabecera() throws Exception {
        crear(PROVEEDORES, adminToken, proveedor("900123456-7", "Aceros Andinos"));

        enviar(get(PROVEEDORES), gerenteNorte, empresa.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
        long creado = leer(postComo(PROVEEDORES, gerenteNorte, empresa.getId(), proveedor("800000001", "Ferretería"))
                .andExpect(status().isCreated())).get("id").asLong();
        assertThat(empresaDe("proveedores", creado)).isEqualTo(norte.getId());
    }

    // --------------------------------------------------------- SUPER_ADMIN

    @Test
    void superAdminSinCabeceraVeTodasLasEmpresasPeroNoEscribe() throws Exception {
        crear(PROVEEDORES, adminToken, proveedor("900123456-7", "Aceros Andinos"));
        crear(PROVEEDORES, gerenteNorte, proveedor("800000001", "Ferretería"));

        getJson(PROVEEDORES, superAdmin).andExpect(jsonPath("$.page.totalElements").value(2));

        postJson(PROVEEDORES, superAdmin, proveedor("700000001", "Sin empresa"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Empresa no seleccionada"));
        mockMvc.perform(post("/api/auth/invitaciones").header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdmin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Empresa no seleccionada"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM proveedores", Integer.class)).isEqualTo(2);
    }

    @Test
    void superAdminConCabeceraActuaDentroDeEsaEmpresa() throws Exception {
        crear(PROVEEDORES, adminToken, proveedor("900123456-7", "Aceros Andinos"));
        crear(PROVEEDORES, gerenteNorte, proveedor("800000001", "Ferretería"));

        enviar(get(PROVEEDORES), superAdmin, norte.getId())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].razonSocial").value("Ferretería"));

        long creado = leer(postComo(PROVEEDORES, superAdmin, norte.getId(), proveedor("700000001", "Soporte"))
                .andExpect(status().isCreated())).get("id").asLong();
        assertThat(empresaDe("proveedores", creado)).isEqualTo(norte.getId());

        // Invitación para Norte: quien la canjea entra en Norte.
        String codigo = leer(postComo("/api/auth/invitaciones", superAdmin, norte.getId(), "{}")
                .andExpect(status().isCreated())).get("codigo").asText();
        mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                        "username", "nuevo.norte", "email", "nuevo@norte.com", "password", "ClaveSegura2026",
                        "codigoInvitacion", codigo))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.empresaId").value(norte.getId()));
    }

    @Test
    void superAdminRevisaSolicitudesConCabeceraPeroNoLasCrea() throws Exception {
        long proveedorUnisen = crear(PROVEEDORES, adminToken, proveedor("900123456-7", "Aceros Andinos"));
        long productoUnisen = crear(PRODUCTOS, adminToken, producto("TOR-001", proveedorUnisen));
        long solicitudUnisen = crear(SOLICITUDES, usuarioToken, solicitud(productoUnisen));
        String cambio = "{\"estado\": \"APROBADA\"}";

        enviar(patch(SOLICITUDES + "/" + solicitudUnisen + "/estado").contentType(MediaType.APPLICATION_JSON)
                .content(cambio), superAdmin, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Empresa no seleccionada"));
        enviar(patch(SOLICITUDES + "/" + solicitudUnisen + "/estado").contentType(MediaType.APPLICATION_JSON)
                .content(cambio), superAdmin, norte.getId())
                .andExpect(status().isNotFound());
        enviar(patch(SOLICITUDES + "/" + solicitudUnisen + "/estado").contentType(MediaType.APPLICATION_JSON)
                .content(cambio), superAdmin, empresa.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisadoPor.username").value("plataforma"));

        postComo(SOLICITUDES, superAdmin, empresa.getId(), solicitud(productoUnisen))
                .andExpect(status().isForbidden());
    }

    @Test
    void laCabeceraDebeSerUnaEmpresaExistente() throws Exception {
        enviar(get(PROVEEDORES), superAdmin, null).andExpect(status().isOk());
        mockMvc.perform(get(PROVEEDORES).header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdmin)
                        .header(TenantFilter.HEADER_TENANT, "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La cabecera X-Tenant-ID debe ser el id numérico de una empresa."));
        enviar(get(PROVEEDORES), superAdmin, 999_999L)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("No existe la empresa con id 999999 indicada en X-Tenant-ID."));
    }

    // ------------------------------------------------------------------ JWT

    @Test
    void elJwtLlevaLaEmpresaSalvoParaElSuperAdmin() {
        Claims norteClaims = jwtUtil.validateToken(gerenteNorte);
        assertThat(JwtUtil.extractEmpresaId(norteClaims)).isEqualTo(norte.getId());
        assertThat(norteClaims.get(JwtUtil.CLAIM_ROL, String.class)).isEqualTo("GERENTE");

        Claims superClaims = jwtUtil.validateToken(superAdmin);
        assertThat(JwtUtil.extractEmpresaId(superClaims)).isNull();
        assertThat(superClaims.get(JwtUtil.CLAIM_ROL, String.class)).isEqualTo("SUPER_ADMIN");
    }

    @Test
    void siElUsuarioCambiaDeEmpresaSusTokensAnterioresDejanDeValer() throws Exception {
        jdbcTemplate.update("UPDATE usuarios SET empresa_id = ? WHERE username = 'compras.norte'", empresa.getId());

        getJson("/api/auth/me", usuarioNorte)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("El token no corresponde a la empresa del usuario. Inicia sesión de nuevo."));
    }

    @Test
    void unaEmpresaDesactivadaBloqueaASusUsuarios() throws Exception {
        jdbcTemplate.update("UPDATE empresas SET activa = FALSE WHERE id = ?", norte.getId());

        getJson("/api/auth/me", gerenteNorte)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("La empresa del usuario está desactivada."));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "gerente.norte", "password", PASSWORD))))
                .andExpect(status().isForbidden());
        // Las demás empresas siguen funcionando.
        getJson("/api/auth/me", adminToken).andExpect(status().isOk());
    }

    // ------------------------------------------------- contexto y ORM

    @Test
    void elContextoDeEmpresaSeLimpiaTrasCadaPeticion() throws Exception {
        getJson(PROVEEDORES, gerenteNorte).andExpect(status().isOk());
        assertThat(TenantContextHolder.obtener()).isEmpty();

        // También cuando la petición termina en error.
        postJson(PROVEEDORES, gerenteNorte, "{}").andExpect(status().isBadRequest());
        assertThat(TenantContextHolder.obtener()).isEmpty();
    }

    @Test
    void hibernateFiltraPorElTenantDelContexto() throws Exception {
        long proveedorUnisen = crear(PROVEEDORES, adminToken, proveedor("900123456-7", "Aceros Andinos"));
        crear(PROVEEDORES, gerenteNorte, proveedor("800000001", "Ferretería"));

        List<String> deNorte = TenantContextHolder.conEmpresa(norte.getId(),
                () -> proveedorRepository.findAll().stream().map(Proveedor::getRazonSocial).toList());
        assertThat(deNorte).containsExactly("Ferretería");
        assertThat(TenantContextHolder.conEmpresa(norte.getId(), () -> proveedorRepository.findById(proveedorUnisen)))
                .as("la carga por id también se filtra").isEmpty();
        // Sin empresa (tenant raíz): todas.
        assertThat(proveedorRepository.findAll()).hasSize(2);
    }
}
