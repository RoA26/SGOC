package com.unisen.sgp.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Unidades recibidas de una línea de la orden. */
public record LineaRecepcionDTO(
        @Schema(example = "1", description = "Id de la línea de la orden (detalles[].id)")
        @NotNull(message = "Indica la línea de la orden.")
        @Positive(message = "La línea no es válida.")
        Long detalleOrdenId,

        @Schema(example = "5")
        @NotNull(message = "Indica la cantidad recibida.")
        @Min(value = 1, message = "La cantidad recibida debe ser al menos 1.")
        @Max(value = 999_999, message = "La cantidad no puede superar 999999.")
        Integer cantidad) {
}
