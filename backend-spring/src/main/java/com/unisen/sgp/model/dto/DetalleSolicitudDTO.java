package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.DetalleSolicitud;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Línea pedida: producto del catálogo y cantidad entera. */
public record DetalleSolicitudDTO(
        @Schema(example = "1")
        @NotNull(message = "Selecciona un producto.")
        @Positive(message = "Selecciona un producto.")
        Long productoId,

        @Schema(example = "10")
        @NotNull(message = "La cantidad es obligatoria.")
        @Positive(message = "La cantidad debe ser mayor que 0.")
        @Max(value = DetalleSolicitud.CANTIDAD_MAXIMA, message = "La cantidad máxima por línea es 999.999.")
        Integer cantidad) {
}
