package com.unisen.sgp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtUtilTest {

    private static final String SECRET = "C3LPGb/hXLJuWguuQNdojKmT+oqHgYVu1ysc5uP5JDww2XPg6n4BThTiam3ZmzUT";
    private static final String OTHER_SECRET = "EAmn61r4UgBaB6CruVK2IcvGTCPxvwRmgbApH3SjMGs5JDA9TSVfffbT9aeBMc5T";
    private static final String ISSUER = "unisen-sgp-test";
    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private JwtUtil jwtUtil;
    private UsuarioPrincipal usuario;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        jwtUtil = jwtUtilAt(NOW, SECRET, ISSUER);
        Empresa empresa = new Empresa("Unisen", "900000001-1");
        asignarId(Empresa.class, empresa, 7L);
        Usuario entidad = new Usuario("Ana.Compras", "Ana@Unisen.com", "$2a$04$hash", "Ana Compras", Rol.USUARIO,
                empresa);
        asignarId(Usuario.class, entidad, 42L);
        usuario = UsuarioPrincipal.from(entidad);
    }

    private static <T> void asignarId(Class<T> tipo, T entidad, Long valor) throws ReflectiveOperationException {
        Field id = tipo.getDeclaredField("id");
        id.setAccessible(true);
        id.set(entidad, valor);
    }

    @Test
    void elTokenDeUnSuperAdminNoLlevaEmpresa() throws ReflectiveOperationException {
        Usuario superAdmin = new Usuario("root", "root@unisen.com", "$2a$04$hash", "Plataforma", Rol.SUPER_ADMIN, null);
        asignarId(Usuario.class, superAdmin, 1L);

        Claims claims = jwtUtil.validateToken(jwtUtil.generateToken(UsuarioPrincipal.from(superAdmin)));

        assertThat(claims.get(JwtUtil.CLAIM_ROL, String.class)).isEqualTo("SUPER_ADMIN");
        assertThat(claims).doesNotContainKey(JwtUtil.CLAIM_EMPRESA_ID);
        assertThat(JwtUtil.extractEmpresaId(claims)).isNull();
    }

    private static JwtUtil jwtUtilAt(Instant instant, String secret, String issuer) {
        return new JwtUtil(new JwtProperties(secret, Duration.ofHours(1), issuer), Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void generaTokenConClaimsEsperados() {
        Claims claims = jwtUtil.validateToken(jwtUtil.generateToken(usuario));

        assertThat(claims.getSubject()).isEqualTo("ana.compras");
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.get(JwtUtil.CLAIM_USER_ID, Long.class)).isEqualTo(42L);
        assertThat(claims.get(JwtUtil.CLAIM_ROL, String.class)).isEqualTo("USUARIO");
        assertThat(JwtUtil.extractEmpresaId(claims)).isEqualTo(7L);
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @Test
    void extraeElNombreDeUsuario() {
        assertThat(jwtUtil.extractUsername(jwtUtil.generateToken(usuario))).isEqualTo("ana.compras");
    }

    @Test
    void cadaTokenTieneUnIdentificadorUnico() {
        String first = jwtUtil.generateToken(usuario);
        String second = jwtUtil.generateToken(usuario);

        assertThat(jwtUtil.validateToken(first).getId()).isNotEqualTo(jwtUtil.validateToken(second).getId());
    }

    @Test
    void rechazaTokenExpirado() {
        String token = jwtUtil.generateToken(usuario);
        JwtUtil dosHorasDespues = jwtUtilAt(NOW.plus(Duration.ofHours(2)), SECRET, ISSUER);

        assertThatThrownBy(() -> dosHorasDespues.validateToken(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void toleraPequenoDesfaseDeReloj() {
        String token = jwtUtil.generateToken(usuario);
        JwtUtil justoTrasExpirar = jwtUtilAt(NOW.plus(Duration.ofHours(1)).plusSeconds(10), SECRET, ISSUER);

        assertThat(justoTrasExpirar.extractUsername(token)).isEqualTo("ana.compras");
    }

    @Test
    void rechazaTokenConFirmaManipulada() {
        String[] partes = jwtUtil.generateToken(usuario).split("\\.");
        String manipulado = partes[0] + "." + partes[1] + "." + new StringBuilder(partes[2]).reverse();

        assertThatThrownBy(() -> jwtUtil.validateToken(manipulado)).isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaTokenFirmadoConOtraClave() {
        String token = jwtUtilAt(NOW, OTHER_SECRET, ISSUER).generateToken(usuario);

        assertThatThrownBy(() -> jwtUtil.validateToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaTokenDeOtroEmisor() {
        String token = jwtUtilAt(NOW, SECRET, "otro-sistema").generateToken(usuario);

        assertThatThrownBy(() -> jwtUtil.validateToken(token)).isInstanceOf(IncorrectClaimException.class);
    }

    @Test
    void rechazaTokenSinFirma() {
        String sinFirma = Jwts.builder()
                .subject("ana.compras").issuer(ISSUER)
                .expiration(Date.from(NOW.plusSeconds(3600)))
                .compact();

        assertThatThrownBy(() -> jwtUtil.validateToken(sinFirma)).isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaTokenSinExpiracion() {
        String sinExp = Jwts.builder()
                .subject("ana.compras").issuer(ISSUER)
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtUtil.validateToken(sinExp))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("exp");
    }

    @Test
    void rechazaTokenVacioOMalFormado() {
        assertThatThrownBy(() -> jwtUtil.validateToken("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jwtUtil.validateToken("no.es.un-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void exigeUnaClaveDeAlMenos256Bits() {
        String claveCorta = "c2hvcnQtc2VjcmV0"; // "short-secret"

        assertThatThrownBy(() -> jwtUtilAt(NOW, claveCorta, ISSUER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    void exigeUnaClaveEnBase64() {
        assertThatThrownBy(() -> jwtUtilAt(NOW, "esto no es base64!!", ISSUER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }
}
