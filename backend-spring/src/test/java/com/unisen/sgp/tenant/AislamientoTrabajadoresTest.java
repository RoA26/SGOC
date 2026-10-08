package com.unisen.sgp.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.support.ApiIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Test 9 del Hito 2: el GERENTE de la empresa A ve y gestiona solo a los trabajadores de A.
 * Usuario no lleva {@code @TenantId}, así que este aislamiento depende de los filtros
 * explícitos por empresa del servicio: aquí se comprueba de extremo a extremo.
 */
class AislamientoTrabajadoresTest extends ApiIntegrationTest {

    private static final String TRABAJADORES = "/api/v1/trabajadores";

    private Empresa empresaB;
    private String gerenteB;
    private long trabajadorA;
    private long trabajadorB;

    @BeforeEach
    void dosEmpresasConUnPendienteCadaUna() throws Exception {
        empresaB = crearEmpresa("Ferretería Norte", "900000002-2");
        gerenteB = crearUsuarioYEntrar("gerente.norte", Rol.GERENTE, empresaB);
        trabajadorA = registrar("pendiente.a", empresa.getCodigoEmpresa());
        trabajadorB = registrar("pendiente.b", empresaB.getCodigoEmpresa());
    }

    private long registrar(String username, String codigoEmpresa) throws Exception {
        return leer(mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "email", username + "@correo.com",
                                "password", "ClaveTrabajador2026", "codigoEmpresa", codigoEmpresa))))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private String estadoEnBd(long id) {
        return jdbcTemplate.queryForObject("SELECT estado FROM usuarios WHERE id = ?", String.class, id);
    }

    @Test
    void cadaCodigoRegistraEnSuPropiaEmpresa() {
        assertThat(jdbcTemplate.queryForObject("SELECT empresa_id FROM usuarios WHERE id = ?", Long.class, trabajadorA))
                .isEqualTo(empresa.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT empresa_id FROM usuarios WHERE id = ?", Long.class, trabajadorB))
                .isEqualTo(empresaB.getId());
    }

    @Test
    void elGerenteAVeSoloASusTrabajadores() throws Exception {
        getJson(TRABAJADORES + "?estado=PENDIENTE", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(trabajadorA));
        getJson(TRABAJADORES, adminToken)
                .andExpect(jsonPath("$.content[?(@.username == 'pendiente.b')]").isEmpty());

        getJson(TRABAJADORES + "?estado=PENDIENTE", gerenteB)
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(trabajadorB));
    }

    @Test
    void elGerenteANoPuedeAprobarRechazarNiDesactivarTrabajadoresDeB() throws Exception {
        for (String estado : new String[] {"ACTIVO", "RECHAZADO", "INACTIVO"}) {
            patchJson(TRABAJADORES + "/" + trabajadorB + "/estado", adminToken, json(Map.of("estado", estado)))
                    .andExpect(status().isNotFound());
        }
        assertThat(estadoEnBd(trabajadorB)).isEqualTo("PENDIENTE");

        // Su propio gerente sí.
        patchJson(TRABAJADORES + "/" + trabajadorB + "/estado", gerenteB, json(Map.of("estado", "ACTIVO")))
                .andExpect(status().isOk());
        assertThat(estadoEnBd(trabajadorB)).isEqualTo("ACTIVO");
    }

    @Test
    void elRegistroPublicoIgnoraElTokenDeOtraEmpresa() throws Exception {
        // Invitación de A canjeada con una petición que, por descuido, lleva el token del gerente de B.
        String invitacionA = leer(postJson("/api/auth/invitaciones", adminToken, "{}")).get("codigo").asText();
        long invitado = leer(mockMvc.perform(post("/api/auth/registro")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + gerenteB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "invitado.a", "email", "invitado.a@correo.com",
                                "password", "ClaveTrabajador2026", "codigoInvitacion", invitacionA))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.empresaId").value(empresa.getId()))).get("id").asLong();
        // Con el código de empresa de A, igual: la empresa la decide el código, nunca el token.
        long conCodigo = leer(mockMvc.perform(post("/api/auth/registro")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + gerenteB)
                        .header(TenantFilter.HEADER_TENANT, empresaB.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "codigo.a", "email", "codigo.a@correo.com",
                                "password", "ClaveTrabajador2026", "codigoEmpresa", empresa.getCodigoEmpresa()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.empresaId").value(empresa.getId()))).get("id").asLong();

        for (long id : new long[] {invitado, conCodigo}) {
            assertThat(jdbcTemplate.queryForObject("SELECT empresa_id FROM usuarios WHERE id = ?", Long.class, id))
                    .isEqualTo(empresa.getId());
        }
    }

    @Test
    void laCabeceraXTenantIdNoCambiaLaEmpresaDeUnGerente() throws Exception {
        String tenantB = empresaB.getId().toString();

        mockMvc.perform(get(TRABAJADORES).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .header(TenantFilter.HEADER_TENANT, tenantB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2)) // "compras" y "pendiente.a", de A
                .andExpect(jsonPath("$.content[?(@.username == 'pendiente.b')]").isEmpty());

        mockMvc.perform(patch(TRABAJADORES + "/" + trabajadorB + "/estado")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .header(TenantFilter.HEADER_TENANT, tenantB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("estado", "ACTIVO"))))
                .andExpect(status().isNotFound());
        assertThat(estadoEnBd(trabajadorB)).isEqualTo("PENDIENTE");

        // Tampoco obtiene el código de la otra empresa.
        mockMvc.perform(get("/api/v1/empresas/actual").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .header(TenantFilter.HEADER_TENANT, tenantB))
                .andExpect(jsonPath("$.id").value(empresa.getId()))
                .andExpect(jsonPath("$.codigoEmpresa").value(empresa.getCodigoEmpresa()));
        getJson("/api/v1/empresas/" + empresaB.getId(), adminToken).andExpect(status().isForbidden());
    }
}
