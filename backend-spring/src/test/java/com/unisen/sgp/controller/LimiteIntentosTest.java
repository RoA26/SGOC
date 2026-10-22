package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.support.ComprasApiTest;
import com.unisen.sgp.support.RelojMutable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Límites de intentos de login y registro de punta a punta (capa A de la auditoría): umbral,
 * 429 con Retry-After y Problem Details, recuperación, normalización, concurrencia, falsificación
 * de X-Forwarded-For y ausencia de regresiones. Umbrales bajos solo para estas pruebas.
 */
@Import(LimiteIntentosTest.RelojDePrueba.class)
@TestPropertySource(properties = {
        "app.security.limites.login.max-fallos=3",
        "app.security.limites.login.ventana=15m",
        "app.security.limites.registro.max-por-codigo=3",
        "app.security.limites.registro.ventana-por-codigo=1h",
        "app.security.limites.registro.max-global=5",
        "app.security.limites.registro.ventana-global=1h"})
class LimiteIntentosTest extends ComprasApiTest {

    private static final String LOGIN = "/api/auth/login";
    private static final String REGISTRO = "/api/auth/registro";

    @TestConfiguration
    static class RelojDePrueba {
        @Bean
        @Primary
        RelojMutable relojMutable() {
            return new RelojMutable(Instant.now());
        }
    }

    @Autowired
    private RelojMutable reloj;
    @Autowired
    private Clock clock;

    @AfterEach
    void elRelojDePruebaEsElQueUsaLaAplicacion() {
        assertThat(clock).isSameAs(reloj);
    }

    private ResultActions intentarLogin(String username, String password, String... ips) throws Exception {
        var peticion = post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", username, "password", password)));
        if (ips.length > 0) {
            peticion.header("X-Forwarded-For", ips[0]).header("X-Real-IP", ips[0]);
        }
        return mockMvc.perform(peticion);
    }

    private ResultActions registrar(String username, Map<String, String> codigo) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>(Map.of("nombres", "Nombre", "apellidos", "De Prueba",
                "username", username, "email", username + "@correo.com", "password", "ClaveTrabajador2026"));
        cuerpo.putAll(codigo);
        return mockMvc.perform(post(REGISTRO).contentType(MediaType.APPLICATION_JSON).content(json(cuerpo)));
    }

    private ResultActions esperarDemasiadosIntentos(ResultActions resultado, long segundos) throws Exception {
        return resultado.andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, Long.toString(segundos)))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Demasiados intentos"))
                .andExpect(jsonPath("$.reintentarEnSegundos").value(segundos));
    }

    // ------------------------------------------------------------------ login

    @Test
    void elLoginCorrectoFuncionaYNoCuentaComoFallo() throws Exception {
        intentarLogin("compras", PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        intentarLogin("compras", "incorrecta").andExpect(status().isUnauthorized());
        intentarLogin("compras", "incorrecta").andExpect(status().isUnauthorized());
        assertThat(limitador.fallosDeLogin("compras")).isEqualTo(2);

        intentarLogin("compras", PASSWORD).andExpect(status().isOk());
        assertThat(limitador.fallosDeLogin("compras")).as("el acierto no suma").isEqualTo(2);
    }

    @Test
    void alAlcanzarElUmbralResponde429ConRetryAfterAunqueLaContrasenaSeaCorrecta() throws Exception {
        for (int i = 1; i <= 3; i++) {
            intentarLogin("compras", "incorrecta").andExpect(status().isUnauthorized());
            assertThat(limitador.fallosDeLogin("compras")).isEqualTo(i);
        }
        esperarDemasiadosIntentos(intentarLogin("compras", "incorrecta"), 900);
        // Bloqueado también con la contraseña correcta: no hay oráculo para seguir probando.
        esperarDemasiadosIntentos(intentarLogin("compras", PASSWORD), 900)
                .andExpect(jsonPath("$.detail").value(
                        "Demasiados intentos fallidos con este usuario. Espera 15 minutos e inténtalo de nuevo."));
        assertThat(limitador.fallosDeLogin("compras")).as("los rechazos por límite no suman").isEqualTo(3);

        // Otro usuario no se ve afectado: no es un bloqueo global.
        intentarLogin("admin", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void laRespuestaEsIgualExistaONoElUsuario() throws Exception {
        for (int i = 0; i < 3; i++) {
            intentarLogin("compras", "incorrecta").andExpect(status().isUnauthorized());
            intentarLogin("no.existe", "incorrecta").andExpect(status().isUnauthorized());
        }
        String existente = intentarLogin("compras", "x").andReturn().getResponse().getContentAsString();
        String inexistente = intentarLogin("no.existe", "x").andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(existente)).isEqualTo(objectMapper.readTree(inexistente));
    }

    @Test
    void seRecuperaAlTerminarLaVentana() throws Exception {
        for (int i = 0; i < 3; i++) {
            intentarLogin("compras", "incorrecta").andExpect(status().isUnauthorized());
            reloj.avanzar(Duration.ofMinutes(1));
        }
        // Fallos en t = 0, 1 y 2 min; ahora t = 3: faltan 12 min para que caduque el primero.
        esperarDemasiadosIntentos(intentarLogin("compras", PASSWORD), 720);

        reloj.avanzar(Duration.ofMinutes(12));
        intentarLogin("compras", PASSWORD).andExpect(status().isOk());
        assertThat(limitador.fallosDeLogin("compras")).as("caducó el primer fallo").isEqualTo(2);

        reloj.avanzar(Duration.ofMinutes(15));
        assertThat(limitador.fallosDeLogin("compras")).isZero();
    }

    @Test
    void losUsuariosEquivalentesTrasNormalizarCompartenElLimite() throws Exception {
        intentarLogin("  COMPRAS ", "incorrecta").andExpect(status().isUnauthorized());
        intentarLogin("Compras", "incorrecta").andExpect(status().isUnauthorized());
        intentarLogin("compras", "incorrecta").andExpect(status().isUnauthorized());
        esperarDemasiadosIntentos(intentarLogin("cOmPrAs", PASSWORD), 900);
    }

    @Test
    void falsificarXForwardedForOXRealIpNoEvitaElLimite() throws Exception {
        for (int i = 0; i < 3; i++) {
            intentarLogin("compras", "incorrecta", "203.0.113." + i).andExpect(status().isUnauthorized());
        }
        esperarDemasiadosIntentos(intentarLogin("compras", PASSWORD, "198.51.100.77"), 900);
        // Y no se puede bloquear a nadie «desde» otra IP: el límite es del usuario, no de la IP.
        intentarLogin("admin", PASSWORD, "203.0.113.0").andExpect(status().isOk());
    }

    @Test
    void lasPeticionesSimultaneasNoSuperanElUmbral() throws Exception {
        for (int ronda = 0; ronda < 3; ronda++) {
            limitador.reiniciar();
            List<Integer> estados = enParalelo(12, i -> () -> intentarLogin("compras", "incorrecta"));
            assertThat(estados).as("ronda %d: %s", ronda, estados).containsOnly(401, 429);
            assertThat(estados.stream().filter(s -> s == 401).count()).as("ronda %d", ronda).isEqualTo(3);
            assertThat(limitador.fallosDeLogin("compras")).isEqualTo(3);
        }
    }

    @Test
    void unaCuentaPendienteConContrasenaCorrectaNoSumaFallos() throws Exception {
        registrar("pendiente.uno", Map.of("codigoEmpresa", empresa.getCodigoEmpresa())).andExpect(status().isCreated());
        for (int i = 0; i < 5; i++) {
            intentarLogin("pendiente.uno", "ClaveTrabajador2026").andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.motivo").value("PENDIENTE"));
        }
        assertThat(limitador.fallosDeLogin("pendiente.uno")).isZero();
    }

    // ------------------------------------------------------------------ registro

    @Test
    void elRegistroRespetaElLimitePorCodigoNormalizado() throws Exception {
        String codigo = empresa.getCodigoEmpresa();
        registrar("cod.uno", Map.of("codigoEmpresa", codigo)).andExpect(status().isCreated());
        registrar("cod.dos", Map.of("codigoEmpresa", codigo.toLowerCase().replace("-", " ")))
                .andExpect(status().isCreated());
        registrar("cod.tres", Map.of("codigoEmpresa", codigo.replace("-", ""))).andExpect(status().isCreated());
        esperarDemasiadosIntentos(registrar("cod.cuatro", Map.of("codigoEmpresa", codigo)), 3600)
                .andExpect(jsonPath("$.detail").value(
                        "Demasiados intentos de registro con este código. Espera 60 minutos e inténtalo de nuevo."));
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE username = 'cod.cuatro'")).isZero();

        // Otra empresa (otro código) no se ve afectada.
        Empresa norte = crearEmpresa("Ferretería Norte", "900000002-2");
        registrar("norte.uno", Map.of("codigoEmpresa", norte.getCodigoEmpresa())).andExpect(status().isCreated());

        reloj.avanzar(Duration.ofHours(1));
        registrar("cod.cinco", Map.of("codigoEmpresa", codigo)).andExpect(status().isCreated());
    }

    @Test
    void losIntentosFallidosConUnCodigoTambienCuentan() throws Exception {
        for (int i = 0; i < 3; i++) {
            registrar("inexistente." + i, Map.of("codigoEmpresa", "ZZZZ-ZZZZ-ZZZZ-ZZZZ"))
                    .andExpect(status().isBadRequest());
        }
        esperarDemasiadosIntentos(registrar("inexistente.x", Map.of("codigoEmpresa", "zzzz zzzz zzzz zzzz")), 3600);
    }

    @Test
    void elRegistroRespetaElLimiteGlobalTambienConInvitaciones() throws Exception {
        String codigo = empresa.getCodigoEmpresa();
        Empresa norte = crearEmpresa("Ferretería Norte", "900000002-2");
        Empresa sur = crearEmpresa("Ferretería Sur", "900000003-3");
        registrar("g.uno", Map.of("codigoEmpresa", codigo)).andExpect(status().isCreated());
        registrar("g.dos", Map.of("codigoEmpresa", norte.getCodigoEmpresa())).andExpect(status().isCreated());
        registrar("g.tres", Map.of("codigoEmpresa", sur.getCodigoEmpresa())).andExpect(status().isCreated());
        String invitacion = leer(postJson("/api/auth/invitaciones", adminToken, "{}")).get("codigo").asText();
        registrar("g.cuatro", Map.of("codigoInvitacion", invitacion)).andExpect(status().isCreated());
        registrar("g.cinco", Map.of("codigoInvitacion", "AAAA-BBBB-CCCC-DDDD")).andExpect(status().isBadRequest());

        String otraInvitacion = leer(postJson("/api/auth/invitaciones", adminToken, "{}")).get("codigo").asText();
        esperarDemasiadosIntentos(registrar("g.seis", Map.of("codigoInvitacion", otraInvitacion)), 3600)
                .andExpect(jsonPath("$.detail").value(
                        "Hay demasiados registros en este momento. Espera 60 minutos e inténtalo de nuevo."));
        // Rechazada por el límite, la invitación no se consumió: sigue siendo válida al recuperarse.
        reloj.avanzar(Duration.ofHours(1));
        registrar("g.seis", Map.of("codigoInvitacion", otraInvitacion)).andExpect(status().isCreated());
    }

    @Test
    void losRegistrosSimultaneosConUnCodigoNoSuperanElUmbral() throws Exception {
        String codigo = empresa.getCodigoEmpresa();
        List<Integer> estados = enParalelo(8, i -> () -> registrar("par." + i, Map.of("codigoEmpresa", codigo)));
        assertThat(estados).as(estados.toString()).containsOnly(201, 429);
        assertThat(estados.stream().filter(s -> s == 201).count()).isEqualTo(3);
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE username LIKE 'par.%'")).isEqualTo(3);
    }

    @Test
    void sinExcederLosLimitesLosFlujosNoCambian() throws Exception {
        String invitacion = leer(postJson("/api/auth/invitaciones", adminToken, "{}")).get("codigo").asText();
        JsonNode activo = leer(registrar("flujo.inv", Map.of("codigoInvitacion", invitacion))
                .andExpect(status().isCreated()));
        assertThat(activo.get("estado").asText()).isEqualTo("ACTIVO");
        intentarLogin("flujo.inv", "ClaveTrabajador2026").andExpect(status().isOk());

        JsonNode pendiente = leer(registrar("flujo.cod", Map.of("codigoEmpresa", empresa.getCodigoEmpresa()))
                .andExpect(status().isCreated()));
        assertThat(pendiente.get("estado").asText()).isEqualTo("PENDIENTE");
        intentarLogin("flujo.cod", "ClaveTrabajador2026").andExpect(status().isForbidden());

        // Errores de siempre, con su código de siempre.
        registrar("flujo.inv", Map.of("codigoEmpresa", empresa.getCodigoEmpresa())).andExpect(status().isConflict());
        intentarLogin("flujo.inv", "incorrecta").andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }
}
