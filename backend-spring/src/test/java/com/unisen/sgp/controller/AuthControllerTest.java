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
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.EstadoUsuario;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.EmpresaRepository;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.security.CodigosInvitacion;
import com.unisen.sgp.security.JwtProperties;
import com.unisen.sgp.security.JwtUtil;
import com.unisen.sgp.security.UsuarioPrincipal;
import com.unisen.sgp.service.UsuarioService;
import com.unisen.sgp.support.BaseDeDatosDePrueba;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmpresaRepository empresaRepository;

    private Empresa empresa;
    private Usuario ana;
    private Usuario inactivo;

    @BeforeEach
    void setUp() {
        BaseDeDatosDePrueba.vaciar(jdbcTemplate);
        empresa = empresaRepository.save(new Empresa("Unisen", "900000001-1", CodigosInvitacion.generar()));
        ana = usuarioService.crearUsuario("Ana.Compras", "Ana.Compras@Unisen.com", "Ana Compras", PASSWORD,
                Rol.USUARIO, empresa);
        inactivo = usuarioService.crearUsuario("baja", "baja@unisen.com", "Usuario de Baja", PASSWORD, Rol.USUARIO,
                empresa);
        inactivo.cambiarEstado(EstadoUsuario.INACTIVO);
        usuarioRepository.save(inactivo);
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))));
    }

    private String tokenFor(String username) throws Exception {
        String body = login(username, PASSWORD).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // ------------------------------------------------------------------ login

    @Test
    void loginCorrectoDevuelveTokenYUsuario() throws Exception {
        String body = login("ana.compras", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(jwtProperties.expiration().toSeconds()))
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.usuario.id").value(ana.getId()))
                .andExpect(jsonPath("$.usuario.username").value("ana.compras"))
                .andExpect(jsonPath("$.usuario.email").value("ana.compras@unisen.com"))
                .andExpect(jsonPath("$.usuario.nombre").value("Ana Compras"))
                .andExpect(jsonPath("$.usuario.rol").value("USUARIO"))
                .andExpect(jsonPath("$.usuario.empresaId").value(empresa.getId()))
                .andExpect(jsonPath("$.usuario.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(body).get("accessToken").asText();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("ana.compras");
    }

    @Test
    void loginNoDistingueMayusculasNiEspaciosEnElUsername() throws Exception {
        login("  ANA.Compras ", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void elCorreoYaNoSirveParaIniciarSesion() throws Exception {
        login("ana.compras@unisen.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos."));
    }

    @Test
    void passwordIncorrectoDevuelve401ProblemDetail() throws Exception {
        login("ana.compras", "incorrecta")
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos."));
    }

    @Test
    void usuarioInexistenteEsIndistinguibleDePasswordIncorrecto() throws Exception {
        login("nadie", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos."));
    }

    @Test
    void cuentaDeshabilitadaConPasswordCorrectoDevuelve403() throws Exception {
        login("baja", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("La cuenta de usuario está deshabilitada."));
    }

    @Test
    void cuentaDeshabilitadaConPasswordIncorrectoNoRevelaSuEstado() throws Exception {
        login("baja", "incorrecta")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos."));
    }

    @Test
    void passwordDeMasDe72BytesNoProvocaErrorInterno() throws Exception {
        login("ana.compras", PASSWORD + "x".repeat(80)).andExpect(status().isUnauthorized());
        login("nadie", "x".repeat(100)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginValidaElCuerpo() throws Exception {
        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"  \",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.errors.username").value("El usuario es obligatorio."))
                .andExpect(jsonPath("$.errors.password").value("La contraseña es obligatoria."));

        login("x".repeat(51), PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").value("El usuario es demasiado largo."));
    }

    @Test
    void loginConElCampoEmailAntiguoDevuelve400() throws Exception {
        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana.compras@unisen.com\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").value("El usuario es obligatorio."));
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
                                Map.of("username", "ana.compras", "password", PASSWORD))))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------- ruta protegida

    @Test
    void meConTokenValidoDevuelveElPerfil() throws Exception {
        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("ana.compras"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ana.getId()))
                .andExpect(jsonPath("$.username").value("ana.compras"))
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
        String token = tokenFor("ana.compras");
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
        String token = tokenFor("ana.compras");
        usuarioRepository.delete(ana);

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("El usuario del token ya no existe."));
    }

    /** Antes del Hito 4 el {@code sub} era el correo: esos tokens dejan de resolver un usuario. */
    @Test
    void tokenAntiguoConElCorreoComoSubjectDevuelve401() throws Exception {
        Usuario comoAntes = new Usuario("ana.compras@unisen.com", ana.getEmail(), ana.getPasswordHash(),
                ana.getNombre(), ana.getRol(), empresa);
        String tokenAntiguo = jwtUtil.generateToken(UsuarioPrincipal.from(comoAntes));

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer(tokenAntiguo)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("El usuario del token ya no existe."));
    }

    @Test
    void desactivarUnUsuarioLeDeniegaElAccesoAunqueSuTokenSigaVigente() throws Exception {
        String token = tokenFor("ana.compras");
        ana.cambiarEstado(EstadoUsuario.INACTIVO);
        usuarioRepository.save(ana);

        // El token sigue siendo válido (identidad conocida): no autorizado → 403, no 401.
        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.title").value("Cuenta no autorizada"))
                .andExpect(jsonPath("$.detail").value("La cuenta de usuario está deshabilitada."))
                .andExpect(jsonPath("$.motivo").value("INACTIVO"));
    }

    @Test
    void cualquierOtraRutaEstaProtegida() throws Exception {
        mockMvc.perform(get("/api/ordenes-compra")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/invitaciones")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void rutaInexistenteConTokenValidoDevuelve404() throws Exception {
        mockMvc.perform(get("/api/no-existe")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("ana.compras"))))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    void laSesionEsStatelessYNoEmiteCookies() throws Exception {
        login("ana.compras", PASSWORD)
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
        assertThat(spec.at("/paths/~1api~1auth~1registro/post/security").isArray()).isTrue();
        assertThat(spec.at("/paths/~1api~1auth~1registro/post/security")).isEmpty();
        // Generar invitaciones hereda el requisito global de bearerAuth.
        assertThat(spec.at("/paths/~1api~1auth~1invitaciones/post").isObject()).isTrue();
        assertThat(spec.at("/paths/~1api~1auth~1invitaciones/post/security").isMissingNode()).isTrue();
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
    void corsPermiteElDominioDeProduccion() throws Exception {
        mockMvc.perform(options(LOGIN_URL)
                        .header(HttpHeaders.ORIGIN, "https://rrtf.duckdns.org")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://rrtf.duckdns.org"));
    }

    @Test
    void corsRechazaElDominioSinHttps() throws Exception {
        mockMvc.perform(options(LOGIN_URL)
                        .header(HttpHeaders.ORIGIN, "http://rrtf.duckdns.org")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
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
