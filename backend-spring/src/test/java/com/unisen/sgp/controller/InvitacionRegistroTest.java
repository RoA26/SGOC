package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.unisen.sgp.support.ApiIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class InvitacionRegistroTest extends ApiIntegrationTest {

    private static final String INVITACIONES_URL = "/api/auth/invitaciones";
    private static final String REGISTRO_URL = "/api/auth/registro";
    private static final String FORMATO_CODIGO = "^[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){3}$";
    private static final String PASSWORD_NUEVO = "OtraClave2026";

    // ------------------------------------------------------------ helpers

    private ResultActions generar(String token) throws Exception {
        return mockMvc.perform(post(INVITACIONES_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String generarCodigo() throws Exception {
        return leer(generar(adminToken).andExpect(status().isCreated())).get("codigo").asText();
    }

    private ResultActions registrar(String username, String email, String password, String codigo) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombres", "Nombre");
        body.put("apellidos", "De Prueba");
        body.put("username", username);
        body.put("email", email);
        body.put("password", password);
        body.put("codigoInvitacion", codigo);
        return registrar(json(body));
    }

    private ResultActions registrar(String body) throws Exception {
        return mockMvc.perform(post(REGISTRO_URL).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Map<String, Object> filaInvitacion(String codigo) {
        return jdbcTemplate.queryForMap("SELECT * FROM codigos_invitacion WHERE codigo = ?", codigo);
    }

    private long idDe(String username) {
        return jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE username = ?", Long.class, username);
    }

    private int usuariosCon(String username) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM usuarios WHERE username = ?", Integer.class,
                username);
    }

    // ------------------------------------------------ generación de códigos

    @Test
    void adminGeneraUnCodigoConCaducidadPorDefectoDe72Horas() throws Exception {
        Instant antes = Instant.now();
        JsonNode respuesta = leer(generar(adminToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").isString()));

        String codigo = respuesta.get("codigo").asText();
        assertThat(codigo).matches(FORMATO_CODIGO);
        Instant expiracion = Instant.parse(respuesta.get("fechaExpiracion").asText());
        assertThat(expiracion).isCloseTo(antes.plus(72, ChronoUnit.HOURS), within(1, ChronoUnit.MINUTES));

        Map<String, Object> fila = filaInvitacion(codigo);
        assertThat(fila.get("usado")).isEqualTo(false);
        assertThat(((Number) fila.get("usuario_creador_id")).longValue()).isEqualTo(idDe("admin"));
        assertThat(fila.get("usado_por_id")).isNull();
    }

    @Test
    void adminPuedeIndicarLasHorasDeValidez() throws Exception {
        Instant antes = Instant.now();
        JsonNode respuesta = leer(postJson(INVITACIONES_URL, adminToken, "{\"horasValidez\": 1}")
                .andExpect(status().isCreated()));

        assertThat(Instant.parse(respuesta.get("fechaExpiracion").asText()))
                .isCloseTo(antes.plus(1, ChronoUnit.HOURS), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void cadaCodigoGeneradoEsDistinto() throws Exception {
        assertThat(List.of(generarCodigo(), generarCodigo(), generarCodigo())).doesNotHaveDuplicates();
    }

    @Test
    void horasDeValidezFueraDeRangoDevuelve400() throws Exception {
        postJson(INVITACIONES_URL, adminToken, "{\"horasValidez\": 0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.horasValidez").value("La validez mínima es de 1 hora."));
        postJson(INVITACIONES_URL, adminToken, "{\"horasValidez\": 721}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.horasValidez").value("La validez máxima es de 720 horas (30 días)."));
    }

    @Test
    void unUsuarioSinRolAdminNoPuedeGenerarCodigos() throws Exception {
        generar(usuarioToken)
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM codigos_invitacion", Integer.class)).isZero();
    }

    @Test
    void generarCodigosSinTokenDevuelve401() throws Exception {
        mockMvc.perform(post(INVITACIONES_URL)).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- registro

    @Test
    void registroConCodigoValidoCreaElUsuarioYConsumeElCodigo() throws Exception {
        String codigo = generarCodigo();

        registrar("Nuevo.Usuario", "Nuevo@Nexora.com", PASSWORD_NUEVO, codigo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("nuevo.usuario"))
                .andExpect(jsonPath("$.email").value("nuevo@nexora.com"))
                .andExpect(jsonPath("$.nombre").value("Nombre De Prueba"))
                .andExpect(jsonPath("$.rol").value("TRABAJADOR"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // Entra en la empresa de quien generó el código.
        assertThat(jdbcTemplate.queryForObject("SELECT empresa_id FROM usuarios WHERE username = ?", Long.class,
                "nuevo.usuario")).isEqualTo(empresa.getId());
        assertThat(((Number) filaInvitacion(codigo).get("empresa_id")).longValue()).isEqualTo(empresa.getId());

        // Contraseña guardada con BCrypt, nunca en claro.
        String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM usuarios WHERE username = ?",
                String.class, "nuevo.usuario");
        assertThat(hash).startsWith("$2a$").doesNotContain(PASSWORD_NUEVO);

        Map<String, Object> fila = filaInvitacion(codigo);
        assertThat(fila.get("usado")).isEqualTo(true);
        assertThat(((Number) fila.get("usado_por_id")).longValue()).isEqualTo(idDe("nuevo.usuario"));
        assertThat(fila.get("fecha_uso")).isNotNull();

        // Puede iniciar sesión de inmediato con su username.
        String token = login("nuevo.usuario", PASSWORD_NUEVO);
        getJson("/api/auth/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("nuevo.usuario"));
    }

    @Test
    void registroGuardaNombresYApellidosPorSeparado() throws Exception {
        registrar(json(Map.of("nombres", "  Ana María ", "apellidos", " Compras Díaz ", "username", "ana",
                        "email", "ana@nexora.com", "password", PASSWORD_NUEVO, "codigoInvitacion", generarCodigo())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombres").value("Ana María"))
                .andExpect(jsonPath("$.apellidos").value("Compras Díaz"))
                .andExpect(jsonPath("$.nombre").value("Ana María Compras Díaz"))
                .andExpect(jsonPath("$.datosPersonalesCompletos").value(true));
        assertThat(jdbcTemplate.queryForObject("SELECT nombres || '|' || apellidos || '|' || nombre FROM usuarios "
                + "WHERE username = 'ana'", String.class)).isEqualTo("Ana María|Compras Díaz|Ana María Compras Díaz");
    }

    @Test
    void registroExigeNombresYApellidosSinConsumirLaInvitacion() throws Exception {
        String codigo = generarCodigo();
        registrar(json(Map.of("username", "sin.nombre", "email", "sin.nombre@nexora.com", "password", PASSWORD_NUEVO,
                        "codigoInvitacion", codigo, "nombres", " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nombres").value("Los nombres son obligatorios."))
                .andExpect(jsonPath("$.errors.apellidos").value("Los apellidos son obligatorios."));
        assertThat(filaInvitacion(codigo).get("usado")).isEqualTo(false);
    }

    @Test
    void elCodigoSeAceptaEnMinusculasSinGuionesYConEspacios() throws Exception {
        String codigo = generarCodigo();
        String comoLoTeclea = " " + codigo.toLowerCase().replace("-", " ") + " ";

        registrar("flexible", "flexible@nexora.com", PASSWORD_NUEVO, comoLoTeclea)
                .andExpect(status().isCreated());
        assertThat(filaInvitacion(codigo).get("usado")).isEqualTo(true);
    }

    @Test
    void unCodigoYaUsadoNoSirveDosVeces() throws Exception {
        String codigo = generarCodigo();
        registrar("primero", "primero@nexora.com", PASSWORD_NUEVO, codigo).andExpect(status().isCreated());

        registrar("segundo", "segundo@nexora.com", PASSWORD_NUEVO, codigo)
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.errors.codigoInvitacion").value("El código de invitación ya fue utilizado."));
        assertThat(usuariosCon("segundo")).isZero();
    }

    @Test
    void unCodigoCaducadoSeRechaza() throws Exception {
        String codigo = generarCodigo();
        jdbcTemplate.update("UPDATE codigos_invitacion SET fecha_expiracion = ? WHERE codigo = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), codigo);

        registrar("tarde", "tarde@nexora.com", PASSWORD_NUEVO, codigo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.codigoInvitacion")
                        .value("El código de invitación ha caducado. Solicita uno nuevo al administrador."));
        assertThat(usuariosCon("tarde")).isZero();
        assertThat(filaInvitacion(codigo).get("usado")).isEqualTo(false);
    }

    @Test
    void unCodigoInexistenteSeRechaza() throws Exception {
        registrar("intruso", "intruso@nexora.com", PASSWORD_NUEVO, "AAAA-BBBB-CCCC-DDDD")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.errors.codigoInvitacion").value("El código de invitación no es válido."));
        assertThat(usuariosCon("intruso")).isZero();
    }

    @Test
    void sinCodigoValidoNoSeRevelaSiElUsernameExiste() throws Exception {
        // "admin" existe, pero la respuesta solo habla del código.
        registrar("admin", "otro@nexora.com", PASSWORD_NUEVO, "AAAA-BBBB-CCCC-DDDD")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.codigoInvitacion").exists())
                .andExpect(jsonPath("$.errors.username").doesNotExist());
    }

    @Test
    void usernameRepetidoDevuelve409YNoConsumeElCodigo() throws Exception {
        String codigo = generarCodigo();

        registrar("  ADMIN ", "distinto@nexora.com", PASSWORD_NUEVO, codigo)
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.errors.username").value("Ese nombre de usuario ya está en uso."));
        assertThat(filaInvitacion(codigo).get("usado")).isEqualTo(false);

        // La transacción se deshizo: el mismo código sigue sirviendo.
        registrar("disponible", "distinto@nexora.com", PASSWORD_NUEVO, codigo).andExpect(status().isCreated());
    }

    @Test
    void emailRepetidoDevuelve409YNoConsumeElCodigo() throws Exception {
        String codigo = generarCodigo();

        registrar("otro.admin", "Admin@Nexora.com", PASSWORD_NUEVO, codigo)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.email").value("Ya existe un usuario con este correo."));
        assertThat(filaInvitacion(codigo).get("usado")).isEqualTo(false);
        assertThat(usuariosCon("otro.admin")).isZero();
    }

    @Test
    void registroValidaTodosLosCampos() throws Exception {
        registrar("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").value("El usuario es obligatorio."))
                .andExpect(jsonPath("$.errors.email").value("El correo es obligatorio."))
                .andExpect(jsonPath("$.errors.password").value("La contraseña es obligatoria."))
                .andExpect(jsonPath("$.errors.codigoInvitacion").value("El código de invitación es obligatorio."));

        String codigo = generarCodigo();
        registrar("a b", "no-es-correo", "corta", codigo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").value(startsWith("El usuario debe tener de 3 a 50 caracteres")))
                .andExpect(jsonPath("$.errors.email").value("El correo no tiene un formato válido."))
                .andExpect(jsonPath("$.errors.password").value("La contraseña debe tener entre 8 y 72 caracteres."));
        // Una petición inválida no llega a tocar el código.
        assertThat(filaInvitacion(codigo).get("usado")).isEqualTo(false);
    }

    @Test
    void usernamesFueraDelPatronSeRechazan() throws Exception {
        String codigo = generarCodigo();
        for (String username : List.of("ab", ".ana", "ana.", "ana@x", "x".repeat(51))) {
            registrar(username, "valido@nexora.com", PASSWORD_NUEVO, codigo)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.username").exists());
        }
    }

    @Test
    void passwordDeMasDe72BytesSeRechazaAunqueTengaMenosDe72Caracteres() throws Exception {
        // 40 caracteres de 2 bytes en UTF-8 = 80 bytes: BCrypt truncaría la contraseña.
        registrar("bytes", "bytes@nexora.com", "ñ".repeat(40), generarCodigo())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("La contraseña no puede superar 72 bytes."));
    }

    @Test
    void registroEsPublicoAunqueLlegueUnTokenInvalido() throws Exception {
        String codigo = generarCodigo();
        mockMvc.perform(post(REGISTRO_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-basura")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombres", "Nombre", "apellidos", "De Prueba", "username", "con.token", "email", "con.token@nexora.com",
                                "password", PASSWORD_NUEVO, "codigoInvitacion", codigo))))
                .andExpect(status().isCreated());
    }

    @Test
    void dosRegistrosSimultaneosConElMismoCodigoSoloCreanUnUsuario() throws Exception {
        String codigo = generarCodigo();
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> resultados = new ArrayList<>();
            for (String username : List.of("carrera.uno", "carrera.dos")) {
                Callable<Integer> tarea = () -> {
                    salida.await();
                    return registrar(username, username + "@nexora.com", PASSWORD_NUEVO, codigo)
                            .andReturn().getResponse().getStatus();
                };
                resultados.add(executor.submit(tarea));
            }
            salida.countDown();

            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> resultado : resultados) {
                estados.add(resultado.get(30, TimeUnit.SECONDS));
            }
            // Uno gana; el otro ve el código usado (400) o, si el bloqueo expira, recibe 409.
            assertThat(estados).containsOnlyOnce(201);
            assertThat(estados).anySatisfy(estado -> assertThat(estado).isIn(400, 409));
        } finally {
            executor.shutdownNow();
        }
        assertThat(usuariosCon("carrera.uno") + usuariosCon("carrera.dos")).isEqualTo(1);
        assertThat(filaInvitacion(codigo).get("usado")).isEqualTo(true);
    }
}
