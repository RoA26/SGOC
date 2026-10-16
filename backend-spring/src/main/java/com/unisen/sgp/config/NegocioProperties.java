package com.unisen.sgp.config;

import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Parámetros del negocio ({@code app.negocio.*}).
 *
 * @param zonaHoraria zona en la que se cuentan los días de la analítica (los instantes se guardan
 *                    en UTC). Por defecto, Colombia: {@code America/Bogota} (UTC−5, sin horario
 *                    de verano)
 */
@Validated
@ConfigurationProperties(prefix = "app.negocio")
public record NegocioProperties(@NotNull ZoneId zonaHoraria) {
}
