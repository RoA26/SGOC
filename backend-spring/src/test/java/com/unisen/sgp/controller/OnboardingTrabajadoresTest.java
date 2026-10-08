package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.security.JwtUtil;
import com.unisen.sgp.security.UsuarioPrincipal;
import com.unisen.sgp.support.ApiIntegrationTest;
import com.unisen.sgp.tenant.TenantFilter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Onboarding de trabajadores (Hito 2): registro con el código de empresa → PENDIENTE → el
 * GERENTE aprueba o rechaza → acceso según el estado de la BD, no según el JWT.
 *
 * <p>Base: "Unisen" ({@code empresa}) con su GERENTE ({@code adminToken}) y un trabajador
 * activo ({@code usuarioToken}).
 */
class OnboardingTrabajadoresTest extends ApiIntegrationTest {

    private static final String REGISTRO = "/api/auth/registro";
    private static final String TRABAJADORES = "/api/v1/trabajadores";
    private static final String SOLICITUDES = "/api/v1/solicitudes";
    private static final String CLAVE = "ClaveTrabajador2026";

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UsuarioRepository usuarioRepository;

    // ------------------------------------------------------------ helpers

    private ResultActions registrar(Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(post(REGISTRO).contentType(MediaType.APPLICATION_JSON).content(json(cuerpo)));
    }

    private ResultActions registrarConCodigo(String username, String codigoEmpresa) throws Exception {
        return registrar(Map.of("username", username, "email", username + "@correo.com", "password", CLAVE,
                "codigoEmpresa", codigoEmpresa));
    }

    /** Registra un trabajador con el código de {@code empresa} y devuelve su id (queda PENDIENTE). */
    private long registrarPendiente(String username) throws Exception {
        return leer(registrarConCodigo(username, empresa.getCodigoEmpresa()).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private ResultActions loginComo(String username) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", username, "password", CLAVE))));
    }

    private ResultActions cambiarEstado(long id, String estado, String token) throws Exception {
        return patchJson(TRABAJADORES + "/" + id + "/estado", token, json(Map.of("estado", estado)));
    }

    /** JWT firmado para el usuario tal como está en la BD (como si lo hubiera obtenido de algún modo). */
    private String tokenFirmadoPara(String username) {
        return jwtUtil.generateToken(UsuarioPrincipal.from(usuarioRepository.findByUsername(username).orElseThrow()));
    }

    private String estadoEnBd(long id) {
        return jdbcTemplate.queryForObject("SELECT estado FROM usuarios WHERE id = ?", String.class, id);
    }

    // ---------------------------------------- Test 3: registro → PENDIENTE

    @Test
    void registroConCodigoDeEmpresaCreaUnTrabajadorPendiente() throws Exception {
        // El cliente no decide rol, estado ni empresa: se ignoran aunque los envíe.
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("username", "Pedro.Bodega");
        cuerpo.put("email", "Pedro@Correo.com");
        cuerpo.put("password", CLAVE);
        cuerpo.put("nombre", "Pedro Bodega");
        // Normalización: minúsculas, sin guiones y con espacios.
        cuerpo.put("codigoEmpresa", " " + empresa.getCodigoEmpresa().replace("-", "").toLowerCase(Locale.ROOT) + " ");
        cuerpo.put("rol", "GERENTE");
        cuerpo.put("estado", "ACTIVO");
        cuerpo.put("empresaId", 999);

        long id = leer(registrar(cuerpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("pedro.bodega"))
                .andExpect(jsonPath("$.nombre").value("Pedro Bodega"))
                .andExpect(jsonPath("$.rol").value("USUARIO"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.empresaId").value(empresa.getId()))
                .andExpect(jsonPath("$.password").doesNotExist())).get("id").asLong();

        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "SELECT rol, estado, activo, empresa_id FROM usuarios WHERE id = ?", id);
        assertThat(fila.get("rol")).isEqualTo("USUARIO");
        assertThat(fila.get("estado")).isEqualTo("PENDIENTE");
        assertThat(fila.get("activo")).isEqualTo(false);
        assertThat(((Number) fila.get("empresa_id")).longValue()).isEqualTo(empresa.getId());
    }

    @Test
    void registroConInvitacionSigueQuedandoActivo() throws Exception {
        // La invitación la genera un gestor: ya autoriza el alta (flujo previo, se conserva).
        String codigo = leer(postJson("/api/auth/invitaciones", adminToken, "{}")).get("codigo").asText();
        registrar(Map.of("username", "invitada", "email", "invitada@correo.com", "password", CLAVE,
                "codigoInvitacion", codigo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
        loginComo("invitada").andExpect(status().isOk());
    }

    // ------------------------------------- Test 4: código inexistente

    @Test
    void registroConCodigoDeEmpresaInexistenteSeRechaza() throws Exception {
        // "admin" existe, pero sin un código válido la respuesta solo habla del código.
        registrarConCodigo("admin", "ZZZZ-ZZZZ-ZZZZ-ZZZZ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.codigoEmpresa").value("El código de empresa no es válido."))
                .andExpect(jsonPath("$.errors.username").doesNotExist());
        registrarConCodigo("nadie", "ZZZZ-ZZZZ-ZZZZ-ZZZZ").andExpect(status().isBadRequest());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM usuarios WHERE username = 'nadie'",
                Integer.class)).isZero();
    }

    @Test
    void registroEnUnaEmpresaDesactivadaSeRechaza() throws Exception {
        jdbcTemplate.update("UPDATE empresas SET activa = FALSE WHERE id = ?", empresa.getId());
        registrarConCodigo("tarde", empresa.getCodigoEmpresa())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.codigoEmpresa").value("La empresa de este código no está activa."));
    }

    @Test
    void registroExigeExactamenteUnCodigo() throws Exception {
        registrar(Map.of("username", "sin.codigo", "email", "s@correo.com", "password", CLAVE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.codigoInvitacion").value("El código de invitación es obligatorio."))
                .andExpect(jsonPath("$.errors.codigoEmpresa")
                        .value("Indica el código de tu empresa o un código de invitación."));
        registrar(Map.of("username", "dos.codigos", "email", "d@correo.com", "password", CLAVE,
                "codigoEmpresa", empresa.getCodigoEmpresa(), "codigoInvitacion", "AAAA-BBBB-CCCC-DDDD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.codigoEmpresa")
                        .value("Usa el código de tu empresa o un código de invitación, no ambos."));
    }

    @Test
    void elRegistroConCodigoDeEmpresaRespetaUnicidadYPolitica() throws Exception {
        registrarConCodigo("compras", empresa.getCodigoEmpresa())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.username").value("Ese nombre de usuario ya está en uso."));
        registrar(Map.of("username", "corta", "email", "corta@correo.com", "password", "corta",
                "codigoEmpresa", empresa.getCodigoEmpresa()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    // ------------------------------- Test 5: PENDIENTE sin acceso operativo

    @Test
    void unPendienteNoRecibeTokenNiAccedeConUnJwtValido() throws Exception {
        registrarPendiente("pendiente");

        // Login: contraseña correcta, pero sin acceso operativo → 403 y ningún token.
        loginComo("pendiente")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Cuenta no autorizada"))
                .andExpect(jsonPath("$.motivo").value("PENDIENTE"))
                .andExpect(jsonPath("$.detail").value("Tu cuenta está pendiente de aprobación por el gerente de tu empresa."))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        // Contraseña incorrecta: no revela que la cuenta existe ni su estado.
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "pendiente", "password", "incorrecta1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.motivo").doesNotExist());

        // Aunque tuviera un JWT válido, la BD manda: 403 en cualquier ruta protegida.
        String token = tokenFirmadoPara("pendiente");
        getJson(SOLICITUDES, token)
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.motivo").value("PENDIENTE"));
        getJson("/api/auth/me", token).andExpect(status().isForbidden());
        getJson("/api/v1/productos", token).andExpect(status().isForbidden());
        postJson(SOLICITUDES, token, "{}").andExpect(status().isForbidden());

        // Las rutas públicas siguen funcionando con ese token en la cabecera.
        mockMvc.perform(post(REGISTRO).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "otro.pendiente", "email", "otro@correo.com",
                                "password", CLAVE, "codigoEmpresa", empresa.getCodigoEmpresa()))))
                .andExpect(status().isCreated());
    }

    // ----------------------------------------------- Test 6: aprobación

    @Test
    void elGerenteApruebaYElTrabajadorObtieneAcceso() throws Exception {
        long id = registrarPendiente("nuevo");

        getJson(TRABAJADORES + "?estado=PENDIENTE", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].username").value("nuevo"))
                .andExpect(jsonPath("$.content[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.content[0].creadoEn").exists());

        cambiarEstado(id, "ACTIVO", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
        assertThat(estadoEnBd(id)).isEqualTo("ACTIVO");
        assertThat(jdbcTemplate.queryForObject("SELECT activo FROM usuarios WHERE id = ?", Boolean.class, id)).isTrue();

        String token = leer(loginComo("nuevo").andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.estado").value("ACTIVO"))).get("accessToken").asText();
        getJson(SOLICITUDES, token).andExpect(status().isOk());
        getJson(TRABAJADORES + "?estado=PENDIENTE", adminToken).andExpect(jsonPath("$.page.totalElements").value(0));
    }

    // ------------------------------------------------- Test 7: rechazo

    @Test
    void elGerenteRechazaYElTrabajadorNoAccede() throws Exception {
        long id = registrarPendiente("rechazado");

        cambiarEstado(id, "RECHAZADO", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"));
        assertThat(estadoEnBd(id)).isEqualTo("RECHAZADO");

        loginComo("rechazado")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.motivo").value("RECHAZADO"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        getJson(SOLICITUDES, tokenFirmadoPara("rechazado"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.motivo").value("RECHAZADO"));
        getJson(TRABAJADORES + "?estado=RECHAZADO", adminToken)
                .andExpect(jsonPath("$.content[0].username").value("rechazado"));
    }

    // ------------------------------- Test 8: CASO CRÍTICO (BD > JWT)

    @Test
    void conElMismoJwtUnTrabajadorDesactivadoRecibe403() throws Exception {
        long id = registrarPendiente("critico");
        cambiarEstado(id, "ACTIVO", adminToken).andExpect(status().isOk());

        // ACTIVO → obtiene su JWT → endpoint protegido: 200.
        String token = leer(loginComo("critico").andExpect(status().isOk())).get("accessToken").asText();
        getJson(SOLICITUDES, token).andExpect(status().isOk());

        // El GERENTE le retira la autorización.
        cambiarEstado(id, "INACTIVO", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVO"));

        // Mismo JWT, todavía criptográficamente válido...
        assertThatCode(() -> jwtUtil.validateToken(token)).doesNotThrowAnyException();
        // ...pero la BD ya no lo autoriza: 403.
        getJson(SOLICITUDES, token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Cuenta no autorizada"))
                .andExpect(jsonPath("$.motivo").value("INACTIVO"));
        getJson("/api/auth/me", token).andExpect(status().isForbidden());

        // Al reactivarlo, el mismo token vuelve a servir: es autorización, no revocación del token.
        cambiarEstado(id, "ACTIVO", adminToken).andExpect(status().isOk());
        getJson(SOLICITUDES, token).andExpect(status().isOk());
    }

    // --------------------------------------------- reglas de la gestión

    @Test
    void soloSePermitenLasTransicionesDefinidas() throws Exception {
        long id = registrarPendiente("transiciones");

        cambiarEstado(id, "INACTIVO", adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Una cuenta en estado PENDIENTE no puede pasar a INACTIVO."));
        cambiarEstado(id, "PENDIENTE", adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.estado").exists());
        cambiarEstado(id, "NO_EXISTE", adminToken).andExpect(status().isBadRequest());
        patchJson(TRABAJADORES + "/" + id + "/estado", adminToken, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.estado").value("El estado es obligatorio."));

        cambiarEstado(id, "RECHAZADO", adminToken).andExpect(status().isOk());
        cambiarEstado(id, "RECHAZADO", adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La cuenta ya está en estado RECHAZADO."));
        // Corregir un rechazo.
        cambiarEstado(id, "ACTIVO", adminToken).andExpect(status().isOk());
        assertThat(estadoEnBd(id)).isEqualTo("ACTIVO");
    }

    @Test
    void soloLosGestoresGestionanTrabajadoresYSoloTrabajadores() throws Exception {
        long id = registrarPendiente("objetivo");

        // Un trabajador no gestiona a nadie.
        getJson(TRABAJADORES, usuarioToken).andExpect(status().isForbidden());
        cambiarEstado(id, "ACTIVO", usuarioToken).andExpect(status().isForbidden());
        assertThat(estadoEnBd(id)).isEqualTo("PENDIENTE");
        mockMvc.perform(get(TRABAJADORES)).andExpect(status().isUnauthorized());

        // Un gestor no cambia el estado de otro gestor ni el suyo: no son trabajadores (404).
        long gerenteId = jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE username = 'admin'", Long.class);
        cambiarEstado(gerenteId, "INACTIVO", adminToken).andExpect(status().isNotFound());
        assertThat(estadoEnBd(gerenteId)).isEqualTo("ACTIVO");
        cambiarEstado(999_999L, "ACTIVO", adminToken).andExpect(status().isNotFound());

        // El listado solo incluye trabajadores (rol USUARIO): "compras" y el nuevo.
        getJson(TRABAJADORES, adminToken)
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.rol != 'USUARIO')]").isEmpty());
    }

    @Test
    void elSuperAdminGestionaTrabajadoresDentroDeUnaEmpresa() throws Exception {
        long id = registrarPendiente("soporte");
        String superAdmin = crearUsuarioYEntrar("plataforma", Rol.SUPER_ADMIN, null);

        // Modo global: no hay empresa en la que gestionar.
        getJson(TRABAJADORES, superAdmin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Empresa no seleccionada"));
        cambiarEstado(id, "ACTIVO", superAdmin).andExpect(status().isBadRequest());

        // Con X-Tenant-ID, dentro de esa empresa.
        mockMvc.perform(get(TRABAJADORES).param("estado", "PENDIENTE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdmin)
                        .header(TenantFilter.HEADER_TENANT, empresa.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("soporte"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch(TRABAJADORES + "/" + id + "/estado")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdmin)
                        .header(TenantFilter.HEADER_TENANT, empresa.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("estado", "ACTIVO"))))
                .andExpect(status().isOk());
        assertThat(estadoEnBd(id)).isEqualTo("ACTIVO");
    }
}
