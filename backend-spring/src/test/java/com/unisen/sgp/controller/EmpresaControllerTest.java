package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.support.ApiIntegrationTest;
import com.unisen.sgp.tenant.TenantFilter;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * Aprovisionamiento de empresas por el SUPER_ADMIN: empresa + código permanente + GERENTE
 * fundador en una sola transacción (Hito 2).
 */
class EmpresaControllerTest extends ApiIntegrationTest {

    private static final String URL = "/api/v1/empresas";
    /** Mismo formato que los códigos de invitación: Base32 de Crockford, XXXX-XXXX-XXXX-XXXX. */
    private static final String FORMATO_CODIGO = "^[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){3}$";

    private String superAdmin;

    @BeforeEach
    void crearSuperAdmin() throws Exception {
        superAdmin = crearUsuarioYEntrar("plataforma", Rol.SUPER_ADMIN, null);
    }

    private static String empresa(String nombre, String nit, String gerenteUsername, String gerenteEmail) {
        return """
                {"nombre": "%s", "nit": "%s",
                 "gerente": {"username": "%s", "email": "%s", "password": "ClaveGerente2026", "nombre": "Laura Gerente"}}
                """.formatted(nombre, nit, gerenteUsername, gerenteEmail);
    }

    private int contar(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    // ------------------------------------------------------- Test 1: crear

    @Test
    void elSuperAdminCreaEmpresaConCodigoYGerenteFundador() throws Exception {
        JsonNode creada = leer(postJson(URL, superAdmin,
                empresa("Ferretería Andina", "901.234.567-8", "Gerente.Andina", "Gerencia@Andina.com"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(".*/api/v1/empresas/\\d+$")))
                .andExpect(jsonPath("$.nombre").value("Ferretería Andina"))
                .andExpect(jsonPath("$.nit").value("901234567-8"))
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.codigoEmpresa").value(matchesPattern(FORMATO_CODIGO)))
                .andExpect(jsonPath("$.gerente.username").value("gerente.andina"))
                .andExpect(jsonPath("$.gerente.email").value("gerencia@andina.com"))
                .andExpect(jsonPath("$.gerente.nombre").value("Laura Gerente"))
                .andExpect(jsonPath("$.gerente.rol").value("GERENTE"))
                .andExpect(jsonPath("$.gerente.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.gerente.password").doesNotExist()));

        long empresaId = creada.get("id").asLong();
        String codigo = creada.get("codigoEmpresa").asText();
        assertThat(creada.get("gerente").get("empresaId").asLong()).isEqualTo(empresaId);

        // En la BD: la empresa con su código y el gerente ACTIVO dentro de ella.
        assertThat(jdbcTemplate.queryForObject("SELECT codigo_empresa FROM empresas WHERE id = ?", String.class,
                empresaId)).isEqualTo(codigo);
        Map<String, Object> gerente = jdbcTemplate.queryForMap(
                "SELECT rol, estado, activo, empresa_id, password_hash FROM usuarios WHERE username = 'gerente.andina'");
        assertThat(gerente.get("rol")).isEqualTo("GERENTE");
        assertThat(gerente.get("estado")).isEqualTo("ACTIVO");
        assertThat(gerente.get("activo")).isEqualTo(true);
        assertThat(((Number) gerente.get("empresa_id")).longValue()).isEqualTo(empresaId);
        assertThat((String) gerente.get("password_hash")).startsWith("$2").isNotEqualTo("ClaveGerente2026");

        // El gerente fundador ya puede entrar y ve el código que compartirá con sus trabajadores.
        String tokenGerente = login("gerente.andina", "ClaveGerente2026");
        getJson(URL + "/actual", tokenGerente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(empresaId))
                .andExpect(jsonPath("$.codigoEmpresa").value(codigo))
                .andExpect(jsonPath("$.gerente").doesNotExist());
    }

    @Test
    void cadaEmpresaRecibeUnCodigoDistintoYEstable() throws Exception {
        String codigoA = leer(postJson(URL, superAdmin, empresa("A", "800000001-1", "gerente.a", "a@a.com"))
                .andExpect(status().isCreated())).get("codigoEmpresa").asText();
        String codigoB = leer(postJson(URL, superAdmin, empresa("B", "800000002-2", "gerente.b", "b@b.com"))
                .andExpect(status().isCreated())).get("codigoEmpresa").asText();
        assertThat(codigoA).isNotEqualTo(codigoB);
        assertThat(codigoA).isNotEqualTo(empresa.getCodigoEmpresa());

        // Permanente: consultarla no lo cambia.
        long idA = jdbcTemplate.queryForObject("SELECT id FROM empresas WHERE nit = '800000001-1'", Long.class);
        getJson(URL + "/" + idA, superAdmin).andExpect(jsonPath("$.codigoEmpresa").value(codigoA));
        getJson(URL + "/" + idA, superAdmin).andExpect(jsonPath("$.codigoEmpresa").value(codigoA));
    }

    // ---------------------------------------------- Test 2: todo o nada

    @Test
    void siElGerenteNoSePuedeCrearNoQuedaNingunaEmpresa() throws Exception {
        int empresasAntes = contar("SELECT COUNT(*) FROM empresas");

        // "admin" ya existe (GERENTE de Unisen): la empresa se inserta primero y luego se deshace.
        postJson(URL, superAdmin, empresa("Huérfana", "811111111-1", "admin", "nuevo@huerfana.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors['gerente.username']").value("Ese nombre de usuario ya está en uso."));
        postJson(URL, superAdmin, empresa("Huérfana", "811111111-1", "nuevo.gerente", "ADMIN@unisen.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors['gerente.email']").value("Ya existe un usuario con este correo."));

        assertThat(contar("SELECT COUNT(*) FROM empresas")).isEqualTo(empresasAntes);
        assertThat(contar("SELECT COUNT(*) FROM empresas WHERE nit = '811111111-1'")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE username = 'nuevo.gerente'")).isZero();

        // Sin restos: el mismo NIT se puede aprovisionar después con un gerente válido.
        postJson(URL, superAdmin, empresa("Huérfana", "811111111-1", "nuevo.gerente", "nuevo@huerfana.com"))
                .andExpect(status().isCreated());
        assertThat(contar("SELECT COUNT(*) FROM usuarios u JOIN empresas e ON e.id = u.empresa_id "
                + "WHERE e.nit = '811111111-1' AND u.rol = 'GERENTE'")).isEqualTo(1);
    }

    @Test
    void nitRepetidoDevuelve409SinCrearAlGerente() throws Exception {
        postJson(URL, superAdmin, empresa("Copia", "900000001-1", "gerente.copia", "copia@copia.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.nit").value("Ya existe una empresa con este NIT."));
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE username = 'gerente.copia'")).isZero();
    }

    // ------------------------------------------------------- validación

    @Test
    void validaLaEmpresaYAlGerente() throws Exception {
        postJson(URL, superAdmin, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nombre").value("El nombre de la empresa es obligatorio."))
                .andExpect(jsonPath("$.errors.nit").value("El NIT es obligatorio."))
                .andExpect(jsonPath("$.errors.gerente").value("Indica los datos del gerente fundador."));

        postJson(URL, superAdmin, """
                {"nombre": "X", "nit": "12", "gerente": {"username": "a b", "email": "no", "password": "corta"}}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nit").exists())
                .andExpect(jsonPath("$.errors['gerente.username']").exists())
                .andExpect(jsonPath("$.errors['gerente.email']").value("El correo no tiene un formato válido."))
                .andExpect(jsonPath("$.errors['gerente.password']").value("La contraseña debe tener entre 8 y 72 caracteres."));
        assertThat(contar("SELECT COUNT(*) FROM empresas WHERE nombre = 'X'")).isZero();
    }

    // ------------------------------------------------------- permisos

    @Test
    void soloElSuperAdminAprovisionaYConsultaEmpresas() throws Exception {
        String cuerpo = empresa("Intrusa", "822222222-2", "gerente.intrusa", "g@intrusa.com");
        postJson(URL, adminToken, cuerpo).andExpect(status().isForbidden());
        postJson(URL, usuarioToken, cuerpo).andExpect(status().isForbidden());
        assertThat(contar("SELECT COUNT(*) FROM empresas WHERE nit = '822222222-2'")).isZero();

        getJson(URL, adminToken).andExpect(status().isForbidden());
        getJson(URL + "/" + empresa.getId(), adminToken).andExpect(status().isForbidden());
        getJson(URL + "/actual", usuarioToken).andExpect(status().isForbidden());
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void elSuperAdminListaYConsultaEmpresas() throws Exception {
        crearEmpresa("Zeta", "833333333-3");
        getJson(URL, superAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].nombre").value("Unisen"))
                .andExpect(jsonPath("$.content[0].codigoEmpresa").value(empresa.getCodigoEmpresa()))
                .andExpect(jsonPath("$.content[1].nombre").value("Zeta"));
        getJson(URL + "/" + empresa.getId(), superAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nit").value("900000001-1"));
        getJson(URL + "/999999", superAdmin).andExpect(status().isNotFound());
    }

    @Test
    void empresaActualSegunElContexto() throws Exception {
        getJson(URL + "/actual", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(empresa.getId()))
                .andExpect(jsonPath("$.codigoEmpresa").value(empresa.getCodigoEmpresa()));
        // SUPER_ADMIN: en modo global no hay empresa actual; con X-Tenant-ID, la elegida.
        getJson(URL + "/actual", superAdmin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Empresa no seleccionada"));
        mockMvc.perform(get(URL + "/actual")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdmin)
                        .header(TenantFilter.HEADER_TENANT, empresa.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Unisen"));
    }
}
