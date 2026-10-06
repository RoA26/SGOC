package com.unisen.sgp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Emisión y validación de tokens JWT firmados con HMAC-SHA256.
 *
 * <p>Claims emitidos: {@code sub} (correo), {@code uid}, {@code rol}, {@code iss},
 * {@code iat}, {@code exp} y {@code jti}. El rol viaja solo como información para el
 * cliente: la autorización del backend siempre se resuelve contra la base de datos.
 */
@Component
public class JwtUtil {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROL = "rol";

    /** Tolerancia ante pequeñas desincronizaciones de reloj entre servidores. */
    private static final long ALLOWED_CLOCK_SKEW_SECONDS = 30;
    private static final int MIN_KEY_BYTES = 32;

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey signingKey;
    private final JwtParser parser;

    public JwtUtil(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.signingKey = buildSigningKey(properties.secret());
        // El parser es inmutable y thread-safe: se construye una única vez.
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .clock(() -> Date.from(clock.instant()))
                .clockSkewSeconds(ALLOWED_CLOCK_SKEW_SECONDS)
                .build();
    }

    /** Genera un token de acceso para el usuario autenticado. */
    public String generateToken(UsuarioPrincipal usuario) {
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .subject(usuario.getUsername())
                .claim(CLAIM_USER_ID, usuario.getId())
                .claim(CLAIM_ROL, usuario.getRol().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(properties.expiration())))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifica firma, emisor y vigencia del token y devuelve sus claims.
     * Los tokens sin firmar ({@code alg: none}) o cifrados se rechazan.
     *
     * @throws io.jsonwebtoken.ExpiredJwtException si el token ha expirado
     * @throws JwtException                        si el token es inválido por cualquier otro motivo
     * @throws IllegalArgumentException            si el token es nulo o vacío
     */
    public Claims validateToken(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        if (!StringUtils.hasText(claims.getSubject())) {
            throw new MalformedJwtException("El token no contiene el claim 'sub'.");
        }
        if (claims.getExpiration() == null) {
            // Defensa en profundidad: un token sin 'exp' sería válido para siempre.
            throw new MalformedJwtException("El token no contiene el claim 'exp'.");
        }
        return claims;
    }

    /** Valida el token y devuelve el nombre de usuario (correo) que contiene. */
    public String extractUsername(String token) {
        return validateToken(token).getSubject();
    }

    public Duration getExpiration() {
        return properties.expiration();
    }

    private static SecretKey buildSigningKey(String base64Secret) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(base64Secret);
        } catch (DecodingException ex) {
            throw new IllegalStateException("app.jwt.secret debe estar codificado en Base64.", ex);
        }
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret debe tener al menos 256 bits (32 bytes) una vez decodificado. "
                            + "Genera uno con: openssl rand -base64 64");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
