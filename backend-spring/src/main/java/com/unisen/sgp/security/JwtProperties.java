package com.unisen.sgp.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Parámetros del JWT ({@code app.jwt.*}). Se validan al arrancar: una configuración
 * incorrecta impide que la aplicación se inicie en lugar de fallar en tiempo de ejecución.
 *
 * @param secret     clave HMAC codificada en Base64 (mínimo 256 bits)
 * @param expiration tiempo de vida del token de acceso
 * @param issuer     emisor ({@code iss}) que se firma y se exige al validar
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank(message = "es obligatorio: define la variable de entorno JWT_SECRET") String secret,
        @NotNull @DurationMin(minutes = 1) Duration expiration,
        @NotBlank String issuer) {

    @Override
    public String toString() {
        // Evita filtrar la clave si las propiedades llegan a un log.
        return "JwtProperties[secret=***, expiration=" + expiration + ", issuer=" + issuer + "]";
    }
}
