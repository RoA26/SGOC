package com.unisen.sgp.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Parámetros de seguridad ({@code app.security.*}).
 *
 * @param bcryptStrength coste de BCrypt (2^n iteraciones)
 * @param cors           orígenes del frontend autorizados a llamar a la API
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @Min(4) @Max(31) int bcryptStrength,
        @Valid @NotNull Cors cors) {

    /**
     * @param allowedOrigins orígenes exactos ({@code esquema://host[:puerto]}, sin ruta ni barra final),
     *                       p. ej. {@code https://n3xora.duckdns.org}. Un valor mal escrito no coincidiría
     *                       nunca con el {@code Origin} del navegador: se rechaza al arrancar.
     */
    public record Cors(@NotEmpty List<@Pattern(regexp = ORIGEN, message = "debe ser esquema://host[:puerto], sin ruta ni barra final") String> allowedOrigins) {

        /** http(s), host (letras, dígitos, punto, guion) y puerto opcional. */
        static final String ORIGEN = "https?://[A-Za-z0-9.-]+(:[0-9]{1,5})?";
    }
}
