package com.unisen.sgp.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }
}
