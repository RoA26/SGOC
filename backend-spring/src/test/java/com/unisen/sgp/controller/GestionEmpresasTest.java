package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.support.ApiIntegrationTest;
import com.unisen.sgp.tenant.TenantFilter;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Gestión centralizada de empresas por el SUPER_ADMIN: código empresarial estable (NASC01),
 * alta atómica con su administrador principal (ADMINISTRADOR_COMERCIAL), listado y ficha, ciclo de vida y
 * supervisión con X-Tenant-ID sin perder la identidad de la cuenta raíz.
 */
class GestionEmpresasTest extends ApiIntegrationTest {

    private static final String URL = "/api/v1/empresas";
    private static final String CLAVE_ADMIN = "ClaveGerente2026";

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private String superAdmin;

    @BeforeEach
    void crearSuperAdmin() throws Exception {
        superAdmin = crearUsuarioYEntrar("plataforma", Rol.SUPER_ADMIN, null);
    }

    // ------------------------------------------------------------ helpers

    private static String empresa(String nombre, String nit, String codigo, String admin) {
        String campoCodigo = codigo == null ? "" : "\"codigo\": \"%s\",".formatted(codigo);
        return """
                {"nombre": "%s", "nit": "%s", %s
                 "administrador": {"nombres": "Light", "apellidos": "Yagami %s", "username": "%s", "email": "%s@%s.com",
                                   "password": "%s"}}
                """.formatted(nombre, nit, campoCodigo, nombre, admin, admin, admin.replace(".", ""), CLAVE_ADMIN);
    }

    private JsonNode crear(String cuerpo) throws Exception {
        return leer(postJson(URL, superAdmin, cuerpo).andExpect(status().isCreated()));
    }

    private ResultActions conTenant(MockHttpServletRequestBuilder peticion, String token, long empresaId) throws Exception {
        return mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(TenantFilter.HEADER_TENANT, Long.toString(empresaId)));
    }

    private int contar(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    // ------------------------------------------------- alta de empresa + administrador

    @Test
    void creaNascarConSuCodigoYSuAdministradorEntraSoloEnSuEmpresa() throws Exception {
        JsonNode nascar = crear(empresa("NASCAR", "901555555-5", "nasc01", "admin.nascar"));
        long nascarId = nascar.get("id").asLong();
        assertThat(nascar.get("codigo").asText()).isEqualTo("NASC01");
        assertThat(nascar.get("administrador").get("rol").asText()).isEqualTo("ADMINISTRADOR_COMERCIAL");
        assertThat(nascar.get("administrador").get("estado").asText()).isEqualTo("ACTIVO");
        assertThat(nascar.get("administrador").get("empresaId").asLong()).isEqualTo(nascarId);
        // El código empresarial no es el código de registro de trabajadores (secreto y aleatorio).
        assertThat(nascar.get("codigoEmpresa").asText()).isNotEqualTo("NASC01").hasSize(19);
        assertThat(jdbcTemplate.queryForObject("SELECT codigo FROM empresas WHERE id = ?", String.class, nascarId))
                .isEqualTo("NASC01");

        // El administrador inicia sesión con el mecanismo existente y trabaja en SU empresa.
        String adminNascar = login("admin.nascar", CLAVE_ADMIN);
        getJson("/api/auth/me", adminNascar)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMINISTRADOR_COMERCIAL"))
                .andExpect(jsonPath("$.empresaId").value(nascarId));
        getJson(URL + "/actual", adminNascar)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("NASC01"));
        long proveedorNascar = leer(postJson("/api/v1/proveedores", adminNascar,
                "{\"nit\": \"900777777-7\", \"razonSocial\": \"Llantas del Sur\", \"email\": \"ventas@llantas.com\"}")
                .andExpect(status().isCreated())).get("id").asLong();

        // No ve ni toca datos de otra empresa, ni aunque envíe X-Tenant-ID.
        long proveedorUnisen = crearProveedor("900123456-7", "Aceros Andinos");
        getJson("/api/v1/proveedores", adminNascar)
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(proveedorNascar));
        getJson("/api/v1/proveedores/" + proveedorUnisen, adminNascar).andExpect(status().isNotFound());
        conTenant(get("/api/v1/proveedores/" + proveedorUnisen), adminNascar, empresa.getId())
                .andExpect(status().isNotFound());
        getJson("/api/v1/proveedores/" + proveedorNascar, adminToken).andExpect(status().isNotFound());
        // Ni administra empresas: eso es exclusivo del SUPER_ADMIN.
        getJson(URL, adminNascar).andExpect(status().isForbidden());
        getJson(URL + "/" + empresa.getId(), adminNascar).andExpect(status().isForbidden());
    }

    @Test
    void sinCodigoSeProponeElSiguienteLibreYNuncaUnRecorteQueColisione() throws Exception {
        assertThat(crear(empresa("NASCAR", "901555555-5", null, "admin.nascar")).get("codigo").asText())
                .isEqualTo("NASC01");
        // Mismo prefijo: el número evita la colisión.
        assertThat(crear(empresa("Nascimento Ltda.", "901666666-6", null, "admin.nascimento")).get("codigo").asText())
                .isEqualTo("NASC02");
        getJson(URL + "/codigo-sugerido?nombre=NASCAR", superAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("NASC03"))
                .andExpect(jsonPath("$.disponible").value(true));
        // Un código vacío equivale a no indicarlo.
        assertThat(crear(empresa("Ñandú S.A.S.", "901777777-7", "  ", "admin.nandu")).get("codigo").asText())
                .isEqualTo("NAND01");
    }

    @Test
    void unCodigoRepetidoDevuelve409SinCrearNiLaEmpresaNiSuAdministrador() throws Exception {
        crear(empresa("NASCAR", "901555555-5", "NASC01", "admin.nascar"));

        postJson(URL, superAdmin, empresa("Otra", "901888888-8", "NASC01", "admin.otra"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.codigo").value("El código NASC01 ya pertenece a otra empresa."));
        // Tampoco con otra capitalización: se normaliza antes de comparar.
        postJson(URL, superAdmin, empresa("Otra", "901888888-8", "Nasc01", "admin.otra"))
                .andExpect(status().isConflict());

        assertThat(contar("SELECT COUNT(*) FROM empresas WHERE nit = '901888888-8'")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE username = 'admin.otra'")).isZero();
    }

    @Test
    void unCodigoConFormatoInvalidoSeRechazaAntesDeCrearNada() throws Exception {
        for (String invalido : new String[] {"1ABC", "AB", "NAS-01", "ÑAND01", "ABCDEFGHIJKLM", "NAS C01"}) {
            postJson(URL, superAdmin, empresa("Formato", "901999999-9", invalido, "admin.formato"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.codigo").exists());
        }
        assertThat(contar("SELECT COUNT(*) FROM empresas WHERE nit = '901999999-9'")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE username = 'admin.formato'")).isZero();
    }

    @Test
    void sinAdministradorNoHayEmpresaYLosDatosDelAdministradorSeValidan() throws Exception {
        postJson(URL, superAdmin, "{\"nombre\": \"Sola\", \"nit\": \"902000000-1\", \"codigo\": \"SOLA01\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.administrador").value("Indica los datos del administrador de la empresa."));
        // Correo de otro usuario (único en toda la plataforma): 409 y ningún resto.
        postJson(URL, superAdmin, """
                {"nombre": "Sola", "nit": "902000000-1", "codigo": "SOLA01",
                 "administrador": {"nombres": "Sola", "apellidos": "Admin", "username": "admin.sola",
                                   "email": "COMPRAS@unisen.com", "password": "ClaveGerente2026"}}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors['administrador.email']").value("Ya existe un usuario con este correo."));
        postJson(URL, superAdmin, """
                {"nombre": "Sola", "nit": "902000000-1", "codigo": "SOLA01",
                 "administrador": {"nombres": "Sola", "apellidos": "Admin", "username": "admin.sola",
                                   "email": "sin-dominio@x", "password": "ClaveGerente2026"}}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['administrador.email']").value("El correo no tiene un formato válido."));
        assertThat(contar("SELECT COUNT(*) FROM empresas WHERE codigo = 'SOLA01'")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE username = 'admin.sola'")).isZero();
    }

    @Test
    void elClienteNoPuedeElegirElRolNiLaEmpresaDelAdministrador() throws Exception {
        JsonNode creada = crear("""
                {"nombre": "Escalada", "nit": "902111111-1", "codigo": "ESCA01",
                 "administrador": {"nombres": "Esca", "apellidos": "Lada", "username": "admin.escalada",
                             "email": "a@escalada.com", "password": "ClaveGerente2026",
                             "rol": "SUPER_ADMIN", "empresaId": %d, "estado": "ACTIVO"}}
                """.formatted(empresa.getId()));
        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "SELECT rol, empresa_id FROM usuarios WHERE username = 'admin.escalada'");
        assertThat(fila.get("rol")).isEqualTo("ADMINISTRADOR_COMERCIAL");
        assertThat(((Number) fila.get("empresa_id")).longValue()).isEqualTo(creada.get("id").asLong());
    }

    // ------------------------------------------------- código: sugerencia y disponibilidad

    @Test
    void sugiereYComprueba() throws Exception {
        mockMvc.perform(get(URL + "/codigo-sugerido").param("nombre", "3M Colombia")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdmin))
                .andExpect(jsonPath("$.codigo").value("MCOL01"));
        mockMvc.perform(get(URL + "/codigo-sugerido").param("nombre", "  ")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdmin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nombre").exists());
        getJson(URL + "/codigo-sugerido", superAdmin).andExpect(status().isBadRequest());

        // "Unisen" (la empresa base de los tests) ya tiene UNIS01.
        getJson(URL + "/codigo-disponible?codigo=unis01", superAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("UNIS01"))
                .andExpect(jsonPath("$.disponible").value(false));
        getJson(URL + "/codigo-disponible?codigo=NASC01", superAdmin)
                .andExpect(jsonPath("$.disponible").value(true));
        getJson(URL + "/codigo-disponible?codigo=N-1", superAdmin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.codigo").exists());
    }

    // ------------------------------------------------- listado y ficha

    @Test
    void elListadoYLaFichaMuestranAlAdministradorPrincipal() throws Exception {
        long nascarId = crear(empresa("NASCAR", "901555555-5", "NASC01", "admin.nascar")).get("id").asLong();
        // Un segundo administrador no desplaza al inicial como administrador principal.
        usuarioService.crearUsuario("otro.admin", "otro@nascar.com", "Otro", "Admin", PASSWORD, Rol.ADMINISTRADOR_COMERCIAL,
                empresaRepository.findById(nascarId).orElseThrow());
        crearEmpresa("Zeta", "833333333-3"); // sin administrador

        getJson(URL + "?sort=nombre", superAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].nombre").value("NASCAR"))
                .andExpect(jsonPath("$.content[0].codigo").value("NASC01"))
                .andExpect(jsonPath("$.content[0].administrador.username").value("admin.nascar"))
                .andExpect(jsonPath("$.content[0].administrador.email").value("admin.nascar@adminnascar.com"))
                .andExpect(jsonPath("$.content[0].administrador.nombre").value("Light Yagami NASCAR"))
                .andExpect(jsonPath("$.content[0].administrador.nombres").value("Light"))
                .andExpect(jsonPath("$.content[0].administrador.password").doesNotExist())
                .andExpect(jsonPath("$.content[1].nombre").value("Unisen"))
                .andExpect(jsonPath("$.content[1].administrador.username").value("admin"))
                .andExpect(jsonPath("$.content[2].nombre").value("Zeta"))
                .andExpect(jsonPath("$.content[2].administrador").doesNotExist());

        getJson(URL + "/" + nascarId, superAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("NASC01"))
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.creadoEn").exists())
                .andExpect(jsonPath("$.administrador.username").value("admin.nascar"));
    }

    @Test
    void elListadoNoHaceUnaConsultaPorEmpresa() throws Exception {
        for (int i = 0; i < 8; i++) {
            crear(empresa("Empresa " + i, "9030000" + i + "0-1", "LOTE0" + i, "admin.lote" + i));
        }
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();

        getJson(URL + "?size=20", superAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(9)));

        // Usuario del JWT + página + total + administradores (una sola consulta). Con N+1 serían más de 10.
        assertThat(estadisticas.getPrepareStatementCount()).isLessThanOrEqualTo(5);
    }

    @Test
    void losUsuariosDeLaFichaSonSoloLosDeEsaEmpresa() throws Exception {
        String codigoRegistro = jdbcTemplate.queryForObject(
                "SELECT codigo_empresa FROM empresas WHERE id = ?", String.class, empresa.getId());
        mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombres", "Nombre", "apellidos", "De Prueba", "username", "pendiente", "email", "pendiente@unisen.com",
                                "password", "ClaveTrabajador2026", "codigoEmpresa", codigoRegistro))))
                .andExpect(status().isCreated());
        long nascarId = crear(empresa("NASCAR", "901555555-5", "NASC01", "admin.nascar")).get("id").asLong();

        getJson(URL + "/" + empresa.getId() + "/usuarios", superAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                // Primero los administradores; después los trabajadores por nombre.
                .andExpect(jsonPath("$.content[0].username").value("admin"))
                .andExpect(jsonPath("$.content[0].rol").value("ADMINISTRADOR_COMERCIAL"))
                .andExpect(jsonPath("$.content[1].username").value("compras"))
                .andExpect(jsonPath("$.content[1].estado").value("ACTIVO"))
                .andExpect(jsonPath("$.content[2].username").value("pendiente"))
                .andExpect(jsonPath("$.content[2].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
        getJson(URL + "/" + nascarId + "/usuarios", superAdmin)
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("admin.nascar"));
        // El tenant de la cabecera no altera la empresa de la ruta.
        conTenant(get(URL + "/" + nascarId + "/usuarios"), superAdmin, empresa.getId())
                .andExpect(jsonPath("$.page.totalElements").value(1));
        getJson(URL + "/999999/usuarios", superAdmin).andExpect(status().isNotFound());
    }

    // ------------------------------------------------- ciclo de vida

    @Test
    void desactivarLaEmpresaBloqueaASusUsuariosYReactivarlaLesDevuelveElAcceso() throws Exception {
        String url = URL + "/" + empresa.getId() + "/estado";
        patchJson(url, superAdmin, "{\"activa\": false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));

        getJson("/api/v1/proveedores", adminToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.motivo").value("EMPRESA_INACTIVA"));
        getJson("/api/v1/solicitudes", usuarioToken).andExpect(status().isForbidden());
        // La cuenta raíz sigue pudiendo consultar sus datos.
        conTenant(get("/api/v1/proveedores"), superAdmin, empresa.getId()).andExpect(status().isOk());
        getJson(URL + "/" + empresa.getId() + "/usuarios", superAdmin).andExpect(status().isOk());

        patchJson(url, superAdmin, "{\"activa\": false}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La empresa ya está desactivada."));
        patchJson(url, superAdmin, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.activa").exists());

        patchJson(url, superAdmin, "{\"activa\": true}").andExpect(status().isOk());
        // El mismo token vuelve a servir.
        getJson("/api/v1/proveedores", adminToken).andExpect(status().isOk());
        patchJson(URL + "/999999/estado", superAdmin, "{\"activa\": false}").andExpect(status().isNotFound());
    }

    @Test
    void elSuperAdminDesactivaYReactivaLaCuentaDelAdministrador() throws Exception {
        long adminId = jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE username = 'admin'", Long.class);
        long usuarioId = jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE username = 'compras'", Long.class);
        long nascarId = crear(empresa("NASCAR", "901555555-5", "NASC01", "admin.nascar")).get("id").asLong();
        String url = URL + "/" + empresa.getId() + "/administradores/" + adminId + "/estado";

        patchJson(url, superAdmin, "{\"estado\": \"INACTIVO\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVO"));
        getJson("/api/auth/me", adminToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.motivo").value("INACTIVO"));
        // Sus trabajadores no se ven afectados.
        getJson("/api/auth/me", usuarioToken).andExpect(status().isOk());

        patchJson(url, superAdmin, "{\"estado\": \"INACTIVO\"}").andExpect(status().isConflict());
        // Solo administradores de la empresa de la ruta: ni un trabajador ni el administrador vía otra empresa.
        patchJson(URL + "/" + empresa.getId() + "/administradores/" + usuarioId + "/estado", superAdmin,
                "{\"estado\": \"INACTIVO\"}").andExpect(status().isNotFound());
        patchJson(URL + "/" + nascarId + "/administradores/" + adminId + "/estado", superAdmin,
                "{\"estado\": \"ACTIVO\"}").andExpect(status().isNotFound());

        patchJson(url, superAdmin, "{\"estado\": \"ACTIVO\"}").andExpect(status().isOk());
        getJson("/api/auth/me", adminToken).andExpect(status().isOk());
    }

    // ------------------------------------------------- autorización

    @Test
    void soloElSuperAdminGestionaEmpresasAunqueSeEnvieXTenantId() throws Exception {
        long adminId = jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE username = 'admin'", Long.class);
        String[] lecturas = {URL + "/codigo-sugerido?nombre=X", URL + "/codigo-disponible?codigo=XYZ01",
                URL + "/" + empresa.getId() + "/usuarios"};
        for (String token : new String[] {adminToken, usuarioToken}) {
            for (String url : lecturas) {
                getJson(url, token).andExpect(status().isForbidden());
                conTenant(get(url), token, empresa.getId()).andExpect(status().isForbidden());
            }
            patchJson(URL + "/" + empresa.getId() + "/estado", token, "{\"activa\": false}")
                    .andExpect(status().isForbidden());
            conTenant(patch(URL + "/" + empresa.getId() + "/administradores/" + adminId + "/estado")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"estado\": \"INACTIVO\"}"), token, empresa.getId())
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(URL + "/codigo-sugerido?nombre=X")).andExpect(status().isUnauthorized());
        assertThat(contar("SELECT COUNT(*) FROM empresas WHERE activa = FALSE")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE estado <> 'ACTIVO'")).isZero();
    }

    // ------------------------------------------------- supervisión

    @Test
    void laSupervisionConservaLaIdentidadDelSuperAdminYSoloMuestraLaEmpresaElegida() throws Exception {
        long nascarId = crear(empresa("NASCAR", "901555555-5", "NASC01", "admin.nascar")).get("id").asLong();
        String adminNascar = login("admin.nascar", CLAVE_ADMIN);
        postJson("/api/v1/proveedores", adminNascar,
                "{\"nit\": \"900777777-7\", \"razonSocial\": \"Llantas del Sur\", \"email\": \"ventas@llantas.com\"}")
                .andExpect(status().isCreated());
        crearProveedor("900123456-7", "Aceros Andinos"); // de Unisen

        // Supervisando NASCAR: sigue siendo SUPER_ADMIN, sin empresa propia.
        conTenant(get("/api/auth/me"), superAdmin, nascarId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.empresaId").doesNotExist());
        conTenant(get("/api/v1/proveedores"), superAdmin, nascarId)
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].razonSocial").value("Llantas del Sur"));
        conTenant(get("/api/v1/solicitudes"), superAdmin, nascarId).andExpect(status().isOk());
        conTenant(get(URL + "/actual"), superAdmin, nascarId).andExpect(jsonPath("$.codigo").value("NASC01"));
        // Conserva sus permisos globales mientras supervisa.
        conTenant(get(URL), superAdmin, nascarId).andExpect(jsonPath("$.page.totalElements").value(2));

        // De vuelta al modo global (explícito): todas las empresas. Sin cabecera, ninguna.
        getGlobal("/api/v1/proveedores", superAdmin).andExpect(jsonPath("$.page.totalElements").value(2));
        getJson("/api/v1/proveedores", superAdmin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Contexto de empresa requerido"));
        conTenant(get("/api/v1/proveedores"), superAdmin, 999999).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------- registro de trabajadores

    @Test
    void elCodigoEmpresarialNoSirveParaRegistrarseYElRegistroConCodigoSecretoSigueFuncionando() throws Exception {
        JsonNode nascar = crear(empresa("NASCAR", "901555555-5", "NASC01", "admin.nascar"));

        // Público y predecible: no concede acceso ni permite crear cuentas.
        mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombres", "Nombre", "apellidos", "De Prueba", "username", "curioso", "email", "curioso@x.com",
                                "password", "ClaveTrabajador2026", "codigoEmpresa", "NASC01"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.codigoEmpresa").value("El código de empresa no es válido."));
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE username = 'curioso'")).isZero();

        // El código de registro (secreto) sigue dando de alta trabajadores PENDIENTES en NASCAR.
        mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombres", "Nombre", "apellidos", "De Prueba", "username", "pedro", "email", "pedro@nascar.com",
                                "password", "ClaveTrabajador2026", "rol", "SUPER_ADMIN",
                                "codigoEmpresa", nascar.get("codigoEmpresa").asText()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("TRABAJADOR"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.empresaId").value(nascar.get("id").asLong()));

        // Y el administrador de NASCAR sigue pudiendo invitar (alta de trabajadores ACTIVOS).
        String adminNascar = login("admin.nascar", CLAVE_ADMIN);
        getJson("/api/v1/trabajadores?estado=PENDIENTE", adminNascar)
                .andExpect(jsonPath("$.content[0].username").value("pedro"));
        postJson("/api/auth/invitaciones", adminNascar, "{}").andExpect(status().isCreated());
    }
}
