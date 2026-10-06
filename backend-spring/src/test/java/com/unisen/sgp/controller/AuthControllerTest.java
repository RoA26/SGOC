package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.security.JwtProperties;
import com.unisen.sgp.security.JwtUtil;
import com.unisen.sgp.security.UsuarioPrincipal;
import com.unisen.sgp.service.UsuarioService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    private static final String LOGIN_URL = "/api/auth/login";
    private static final String ME_URL = "/api/auth/me";
    private static final String PASSWORD = "S3gura!Password";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private JwtProperties jwtProperties;

    private Usuario ana;
    private Usuario inactivo;

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();
        ana = usuarioService.crearUsuario("Ana.Compras@Unisen.com", "Ana Compras", PASSWORD, Rol.USUARIO);
        inactivo = usuarioService.crearUsuario("baja@unisen.com", "Usuario de Baja", PASSWORD, Rol.USUARIO);
        inactivo.setActivo(false);
        usuarioRepository.save(inactivo);
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private String tokenFor(String email) throws Exception {
        String body = login(email, PASSWORD).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // ------------------------------------------------------------------ login

    @Test
    void loginCorrectoDevuelveTokenYUsuario() throws Exception {
        String body = login("ana.compras@unisen.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(jwtProperties.expiration().toSeconds()))
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.usuario.id").value(ana.getId()))
                .andExpect(jsonPath("$.usuario.email").value("ana.compras@unisen.com"))
                .andExpect(jsonPath("$.usuario.nombre").value("Ana Compras"))
                .andExpect(jsonPath("$.usuario.rol").value("USUARIO"))
                .andExpect(jsonPath("$.usuario.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(body).get("accessToken").asText();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("ana.compras@unisen.com");
    }

    @Test
    void loginNoDistingueMayusculasNiEspaciosEnElCorreo() throws Exception {
        login("  ANA.COMPRAS@UNISEN.COM ", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void passwordIncorrectoDevuelve401ProblemDetail() throws Exception {
        login("ana.compras@unisen.com", "incorrecta")
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Correo o contraseña incorrectos."));
    }

    @Test
    void correoInexistenteEsIndistinguibleDePasswordIncorrecto() throws Exception {
        login("nadie@unisen.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Correo o contraseña incorrectos."));
    }

    @Test
    void cuentaDeshabilitadaConPasswordCorrectoDevuelve403() throws Exception {
        login("baja@unisen.com", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("La cuenta de usuario está deshabilitada."));
    }

    @Test
    void cuentaDeshabilitadaConPasswordIncorrectoNoRevelaSuEstado() throws Exception {
        login("baja@unisen.com", "incorrecta")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Correo o contraseña incorrectos."));
    }

    @Test
    void passwordDeMasDe72BytesNoProvocaErrorInterno() throws Exception {
        login("ana.compras@unisen.com", PASSWORD + "x".repeat(80)).andExpect(status().isUnauthorized());
        login("nadie@unisen.com", "x".repeat(100)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginValidaElCuerpo() throws Exception {
        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no-es-correo\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.errors.email").value("El correo no tiene un formato válido."))
                .andExpect(jsonPath("$.errors.password").value("La contraseña es obligatoria."));
    }

    @Test
    void loginConJsonMalFormadoDevuelve400() throws Exception {
        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content("{email:"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    void loginEsPublicoAunqueLlegueUnTokenInvalido() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer("token-caducado-o-basura"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "ana.compras@unisen.com", "password", PASSWORD))))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------- ruta protegida

    @Test
    void meConTokenValidoDevuelveElPerfil() throws Exception {
        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("ana.compras@unisen.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ana.getId()))
                .andExpect(jsonPath("$.email").value("ana.compras@unisen.com"))
                .andExpect(jsonPath("$.rol").value("USUARIO"));
    }

    @Test
    void meSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.detail").value("Se requiere autenticación."))
                .andExpect(jsonPath("$.instance").value(ME_URL));
    }

    @Test
    void meConEsquemaDistintoDeBearerDevuelve401() throws Exception {
        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meConTokenManipuladoDevuelve401() throws Exception {
        String token = tokenFor("ana.compras@unisen.com");
        String manipulado = token.substring(0, token.length() - 4) + "AAAA";

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer(manipulado)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("El token no es válido."));
    }

    @Test
    void meConTokenExpiradoDevuelve401() throws Exception {
        JwtUtil ayer = new JwtUtil(jwtProperties, Clock.fixed(Instant.now().minus(Duration.ofDays(1)), ZoneOffset.UTC));
        String expirado = ayer.generateToken(UsuarioPrincipal.from(ana));

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer(expirado)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("El token ha expirado."));
    }

    @Test
    void tokenDeUsuarioEliminadoDevuelve401() throws Exception {
        String token = tokenFor("ana.compras@unisen.com");
        usuarioRepository.delete(ana);

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("El usuario del token ya no existe."));
    }

    @Test
    void desactivarUnUsuarioRevocaSusTokensAlInstante() throws Exception {
        String token = tokenFor("ana.compras@unisen.com");
        ana.setActivo(false);
        usuarioRepository.save(ana);

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("La cuenta de usuario está deshabilitada."));
    }

    @Test
    void cualquierOtraRutaEstaProtegida() throws Exception {
        mockMvc.perform(get("/api/ordenes-compra")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/registro")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void rutaInexistenteConTokenValidoDevuelve404() throws Exception {
        mockMvc.perform(get("/api/no-existe")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("ana.compras@unisen.com"))))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    void laSesionEsStatelessYNoEmiteCookies() throws Exception {
        login("ana.compras@unisen.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    // ------------------------------------------------------- infraestructura

    @Test
    void healthEsPublico() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void documentacionOpenApiEsPublicaYDeclaraBearer() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode spec = objectMapper.readTree(body);
        assertThat(spec.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(spec.at("/paths/~1api~1auth~1login/post/security").isArray()).isTrue();
        assertThat(spec.at("/paths/~1api~1auth~1login/post/security")).isEmpty();
    }

    @Test
    void corsPermiteElOrigenDelFrontend() throws Exception {
        mockMvc.perform(options(LOGIN_URL)
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void corsRechazaOrigenesDesconocidos() throws Exception {
        mockMvc.perform(options(LOGIN_URL)
                        .header(HttpHeaders.ORIGIN, "https://malicioso.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
