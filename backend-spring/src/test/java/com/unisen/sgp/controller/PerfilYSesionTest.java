package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.security.JwtProperties;
import com.unisen.sgp.support.ApiIntegrationTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Identidad de la sesión: nombres y apellidos, rol y empresa (nombre comercial y código) salen de
 * la BD en el login y en cada {@code GET /api/auth/me}; cada persona corrige sus propios datos.
 */
class PerfilYSesionTest extends ApiIntegrationTest {

    private static final String ME = "/api/auth/me";

    @Autowired
    private JwtProperties jwtProperties;

    private long idDe(String username) {
        return jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE username = ?", Long.class, username);
    }

    @Test
    void elLoginYElPerfilTraenNombresRolYEmpresaReales() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.nombres").value("Admin"))
                .andExpect(jsonPath("$.usuario.apellidos").value("Unisen"))
                .andExpect(jsonPath("$.usuario.nombre").value("Admin Unisen"))
                .andExpect(jsonPath("$.usuario.datosPersonalesCompletos").value(true))
                .andExpect(jsonPath("$.usuario.rol").value("ADMINISTRADOR_COMERCIAL"))
                .andExpect(jsonPath("$.usuario.empresaNombre").value("Unisen"))
                .andExpect(jsonPath("$.usuario.empresaCodigo").value("UNIS01"));

        getJson(ME, usuarioToken)
                .andExpect(jsonPath("$.rol").value("TRABAJADOR"))
                .andExpect(jsonPath("$.empresaId").value(empresa.getId()))
                .andExpect(jsonPath("$.empresaNombre").value("Unisen"));

        // El SUPER_ADMIN no pertenece a ninguna empresa.
        String superAdmin = crearUsuarioYEntrar("plataforma", Rol.SUPER_ADMIN, null);
        getJson(ME, superAdmin)
                .andExpect(jsonPath("$.rol").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.empresaId").doesNotExist())
                .andExpect(jsonPath("$.empresaNombre").doesNotExist());
    }

    @Test
    void unCambioDeNombreComercialSeVeSinVolverAIniciarSesion() throws Exception {
        jdbcTemplate.update("UPDATE empresas SET nombre = 'Unisen Global S.A.S.' WHERE id = ?", empresa.getId());

        // Mismo token: el perfil se lee de la BD, no del JWT.
        getJson(ME, adminToken).andExpect(jsonPath("$.empresaNombre").value("Unisen Global S.A.S."));
    }

    @Test
    void unaCuentaAnteriorConservaSuNombreYLoCompletaDesdeSuPerfil() throws Exception {
        // Como quedan las cuentas anteriores a V9: nombre completo y sin nombres ni apellidos.
        jdbcTemplate.update("UPDATE usuarios SET nombres = NULL, apellidos = NULL, nombre = ? WHERE username = ?",
                "María del Pilar De la Torre", "compras");
        getJson(ME, usuarioToken)
                .andExpect(jsonPath("$.nombre").value("María del Pilar De la Torre"))
                .andExpect(jsonPath("$.nombres").doesNotExist())
                .andExpect(jsonPath("$.datosPersonalesCompletos").value(false));

        patchJson(ME, usuarioToken, json(Map.of("nombres", " María del Pilar ", "apellidos", "De la Torre")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombres").value("María del Pilar"))
                .andExpect(jsonPath("$.apellidos").value("De la Torre"))
                .andExpect(jsonPath("$.nombre").value("María del Pilar De la Torre"))
                .andExpect(jsonPath("$.datosPersonalesCompletos").value(true))
                .andExpect(jsonPath("$.empresaNombre").value("Unisen"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT nombres || '|' || apellidos FROM usuarios WHERE username = 'compras'", String.class))
                .isEqualTo("María del Pilar|De la Torre");
        // Las demás cuentas no cambian.
        assertThat(jdbcTemplate.queryForObject("SELECT nombre FROM usuarios WHERE username = 'admin'", String.class))
                .isEqualTo("Admin Unisen");
    }

    @Test
    void elPerfilSoloCambiaNombresYApellidosDeLaPropiaCuenta() throws Exception {
        patchJson(ME, usuarioToken, json(Map.of("nombres", "", "apellidos", "x".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nombres").value("Los nombres son obligatorios."))
                .andExpect(jsonPath("$.errors.apellidos").value("Los apellidos no pueden superar 100 caracteres."));

        // Rol, empresa, estado, username o id de otra cuenta en el JSON se ignoran.
        patchJson(ME, usuarioToken, json(Map.of("nombres", "Carla", "apellidos", "Compras", "rol", "SUPER_ADMIN",
                        "empresaId", 999, "estado", "ACTIVO", "username", "admin", "id", idDe("admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("compras"))
                .andExpect(jsonPath("$.rol").value("TRABAJADOR"))
                .andExpect(jsonPath("$.empresaId").value(empresa.getId()));
        assertThat(jdbcTemplate.queryForObject("SELECT nombre FROM usuarios WHERE username = 'admin'", String.class))
                .isEqualTo("Admin Unisen");

        mockMvc.perform(patch(ME).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombres", "A", "apellidos", "B"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenEmitidoConElRolAntiguoSigueValiendoPorqueElRolSaleDeLaBd() throws Exception {
        Instant ahora = Instant.now();
        String tokenAntiguo = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(jwtProperties.issuer())
                .subject("admin")
                .claim("uid", idDe("admin"))
                .claim("rol", "GERENTE")
                .claim("empresaId", empresa.getId())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret())), Jwts.SIG.HS256)
                .compact();

        getJson(ME, tokenAntiguo).andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("ADMINISTRADOR_COMERCIAL"));
        // Sus permisos son los de la BD: administra su empresa, nada global.
        postJson("/api/v1/proveedores", tokenAntiguo,
                "{\"nit\": \"900123456-7\", \"razonSocial\": \"Aceros\", \"email\": \"v@aceros.com\"}")
                .andExpect(status().isCreated());
        getJson("/api/v1/empresas", tokenAntiguo).andExpect(status().isForbidden());
    }
}
