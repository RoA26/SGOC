package com.unisen.sgp.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Límites de intentos de login y registro ({@code app.security.limites.*}). Se cuentan en memoria
 * por clave (nunca por IP): ventanas deslizantes que se recuperan solas, sin bloqueos permanentes.
 *
 * @param login    intentos fallidos por usuario (username normalizado)
 * @param registro altas por código de empresa (normalizado) y altas en total
 * @param maxClaves claves vigiladas a la vez por cada límite (cota de memoria)
 */
@Validated
@ConfigurationProperties(prefix = "app.security.limites")
public record LimitesProperties(
        @Valid @NotNull Login login,
        @Valid @NotNull Registro registro,
        @Min(100) @Max(1_000_000) int maxClaves) {

    /**
     * @param maxFallos credenciales incorrectas admitidas por usuario dentro de la ventana
     * @param ventana   tiempo durante el que cuenta cada fallo
     */
    public record Login(@Min(1) @Max(1000) int maxFallos, @NotNull Duration ventana) {
    }

    /**
     * @param maxPorCodigo  intentos de alta con un mismo código de empresa dentro de su ventana
     * @param ventanaPorCodigo tiempo durante el que cuenta cada intento con ese código
     * @param maxGlobal     intentos de alta de cualquier tipo dentro de la ventana global
     * @param ventanaGlobal tiempo durante el que cuenta cada intento para el total
     */
    public record Registro(@Min(1) @Max(100_000) int maxPorCodigo, @NotNull Duration ventanaPorCodigo,
                           @Min(1) @Max(1_000_000) int maxGlobal, @NotNull Duration ventanaGlobal) {
    }
}
