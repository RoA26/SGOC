package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.support.ApiIntegrationTest;
import com.unisen.sgp.tenant.TenantFilter;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Gestión de invitaciones por el administrador de cada empresa: listado sin exponer el código,
 * revocación auditada y su efecto en el registro, también ante un canje simultáneo.
 */
class GestionInvitacionesTest extends ApiIntegrationTest {

    private static final String URL = "/api/auth/invitaciones";
    private static final String CLAVE = "ClaveTrabajador2026";

    private Empresa norte;
    private String adminNorte;

    @BeforeEach
    void crearOtraEmpresa() throws Exception {
        norte = crearEmpresa("Norte", "900000002-2");
        adminNorte = crearUsuarioYEntrar("admin.norte", Rol.ADMINISTRADOR_COMERCIAL, norte);
    }

    // ------------------------------------------------------------ helpers

    private JsonNode generar(String token) throws Exception {
        return leer(postJson(URL, token, "{}").andExpect(status().isCreated()));
    }

    private long idDe(String codigo) {
        return jdbcTemplate.queryForObject("SELECT id FROM codigos_invitacion WHERE codigo = ?", Long.class, codigo);
    }

    private ResultActions registrar(String username, String codigo) throws Exception {
        return mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("nombres", "Nombre", "apellidos", "De Prueba", "username", username,
                        "email", username + "@correo.com", "password", CLAVE, "codigoInvitacion", codigo))));
    }

    private ResultActions revocar(long id, String token) throws Exception {
        return deleteJson(URL + "/" + id, token);
    }

    private int contar(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    // ------------------------------------------------------------ listado

    @Test
    void cadaAdministradorListaSoloLasDeSuEmpresaYNuncaVeElCodigoCompleto() throws Exception {
        String primero = generar(adminToken).get("codigo").asText();
        generar(adminToken);
        generar(adminNorte);

        JsonNode pagina = leer(getJson(URL, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].estado").value("VIGENTE"))
                .andExpect(jsonPath("$.content[0].creador.username").value("admin"))
                .andExpect(jsonPath("$.content[0].codigo").doesNotExist()));
        // La más antigua va al final; solo se reconoce por sus 4 últimos caracteres.
        assertThat(pagina.get("content").get(1).get("codigoParcial").asText())
                .isEqualTo("••••-••••-••••-" + primero.substring(primero.length() - 4))
                .doesNotContain(primero.substring(0, 4));

        getJson(URL, adminNorte).andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void elEstadoReflejaUsoRevocacionYCaducidad() throws Exception {
        String usada = generar(adminToken).get("codigo").asText();
        String revocada = generar(adminToken).get("codigo").asText();
        String caducada = generar(adminToken).get("codigo").asText();
        generar(adminToken);
        registrar("pedro", usada).andExpect(status().isCreated());
        revocar(idDe(revocada), adminToken).andExpect(status().isOk());
        jdbcTemplate.update("UPDATE codigos_invitacion SET fecha_expiracion = ? WHERE codigo = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), caducada);

        JsonNode contenido = leer(getJson(URL, adminToken)).get("content");
        List<String> estados = new ArrayList<>();
        contenido.forEach(fila -> estados.add(fila.get("estado").asText()));
        assertThat(estados).containsExactlyInAnyOrder("VIGENTE", "CADUCADA", "REVOCADA", "USADA");
        contenido.forEach(fila -> {
            switch (fila.get("estado").asText()) {
                case "USADA" -> assertThat(fila.get("usadoPor").get("username").asText()).isEqualTo("pedro");
                case "REVOCADA" -> {
                    assertThat(fila.get("revocadoPor").get("username").asText()).isEqualTo("admin");
                    assertThat(fila.get("fechaRevocacion").isNull()).isFalse();
                }
                default -> assertThat(fila.has("usadoPor")).isFalse();
            }
        });
    }

    // ------------------------------------------------------------ revocación

    @Test
    void unaInvitacionRevocadaYaNoPermiteRegistrarse() throws Exception {
        String codigo = generar(adminToken).get("codigo").asText();
        long id = idDe(codigo);

        revocar(id, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REVOCADA"))
                .andExpect(jsonPath("$.revocadoPor.username").value("admin"));

        registrar("tarde", codigo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.codigoInvitacion")
                        .value("El código de invitación fue revocado. Solicita uno nuevo al administrador."));
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE username = 'tarde'")).isZero();
        // Baja lógica: la fila se conserva con quién la revocó y cuándo.
        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "SELECT usado, fecha_revocacion, revocado_por_id FROM codigos_invitacion WHERE id = ?", id);
        assertThat(fila.get("usado")).isEqualTo(false);
        assertThat(fila.get("fecha_revocacion")).isNotNull();
        assertThat(((Number) fila.get("revocado_por_id")).longValue()).isEqualTo(
                jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE username = 'admin'", Long.class));

        revocar(id, adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La invitación ya estaba revocada."));
    }

    @Test
    void noSeRevocaUnaInvitacionUsadaNiUnaCaducada() throws Exception {
        String usada = generar(adminToken).get("codigo").asText();
        registrar("pedro", usada).andExpect(status().isCreated());
        revocar(idDe(usada), adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La invitación ya fue utilizada: no se puede revocar."));

        String caducada = generar(adminToken).get("codigo").asText();
        jdbcTemplate.update("UPDATE codigos_invitacion SET fecha_expiracion = ? WHERE codigo = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), caducada);
        revocar(idDe(caducada), adminToken).andExpect(status().isConflict());
        assertThat(contar("SELECT COUNT(*) FROM codigos_invitacion WHERE fecha_revocacion IS NOT NULL")).isZero();
    }

    // ------------------------------------------------------------ aislamiento y permisos

    @Test
    void unAdministradorNoGestionaInvitacionesDeOtraEmpresaYUnTrabajadorNingunas() throws Exception {
        String codigo = generar(adminToken).get("codigo").asText();
        long id = idDe(codigo);

        // Para Norte, la invitación de Unisen no existe.
        revocar(id, adminNorte).andExpect(status().isNotFound());
        mockMvc.perform(delete(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminNorte)
                        .header(TenantFilter.HEADER_TENANT, empresa.getId().toString()))
                .andExpect(status().isNotFound());

        getJson(URL, usuarioToken).andExpect(status().isForbidden());
        revocar(id, usuarioToken).andExpect(status().isForbidden());
        postJson(URL, usuarioToken, "{}").andExpect(status().isForbidden());

        registrar("sigue.valida", codigo).andExpect(status().isCreated());
    }

    @Test
    void elSuperAdminSoloGestionaInvitacionesDentroDeUnaEmpresaConcreta() throws Exception {
        String superAdmin = crearUsuarioYEntrar("plataforma", Rol.SUPER_ADMIN, null);
        long id = idDe(generar(adminToken).get("codigo").asText());

        getJson(URL, superAdmin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Empresa no seleccionada"));
        getGlobal(URL, superAdmin).andExpect(status().isBadRequest());

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdmin)
                        .header(TenantFilter.HEADER_TENANT, empresa.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mockMvc.perform(delete(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdmin)
                        .header(TenantFilter.HEADER_TENANT, empresa.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revocadoPor.username").value("plataforma"));
    }

    // ------------------------------------------------------------ concurrencia

    @Test
    void revocarYCanjearALaVezNuncaDejanUnEstadoIncoherente() throws Exception {
        for (int ronda = 0; ronda < 5; ronda++) {
            String codigo = generar(adminToken).get("codigo").asText();
            long id = idDe(codigo);
            String username = "carrera." + ronda;
            CountDownLatch salida = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            int estadoRevocacion;
            int estadoRegistro;
            try {
                Callable<Integer> revocacion = () -> {
                    salida.await();
                    return revocar(id, adminToken).andReturn().getResponse().getStatus();
                };
                Callable<Integer> registro = () -> {
                    salida.await();
                    return registrar(username, codigo).andReturn().getResponse().getStatus();
                };
                Future<Integer> r1 = executor.submit(revocacion);
                Future<Integer> r2 = executor.submit(registro);
                salida.countDown();
                estadoRevocacion = r1.get(30, TimeUnit.SECONDS);
                estadoRegistro = r2.get(30, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }

            Map<String, Object> fila = jdbcTemplate.queryForMap(
                    "SELECT usado, fecha_revocacion FROM codigos_invitacion WHERE id = ?", id);
            int usuarios = contar("SELECT COUNT(*) FROM usuarios WHERE username = ?", username);
            boolean usada = Boolean.TRUE.equals(fila.get("usado"));
            boolean revocada = fila.get("fecha_revocacion") != null;
            // Exactamente uno de los dos gana, y la BD lo refleja de forma coherente.
            assertThat(usada ^ revocada).as("ronda %d: usada xor revocada", ronda).isTrue();
            assertThat(usuarios).isEqualTo(usada ? 1 : 0);
            // Quien pierde ve la invitación ya consumida (o, si expira la espera del bloqueo, 409).
            if (usada) {
                assertThat(estadoRegistro).as("ronda %d", ronda).isEqualTo(201);
                assertThat(estadoRevocacion).as("ronda %d", ronda).isEqualTo(409);
            } else {
                assertThat(estadoRevocacion).as("ronda %d", ronda).isEqualTo(200);
                assertThat(estadoRegistro).as("ronda %d", ronda).isIn(400, 409);
            }
        }
    }
}
