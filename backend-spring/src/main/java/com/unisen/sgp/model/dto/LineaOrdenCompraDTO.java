package com.unisen.sgp.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Línea de una orden de compra en la petición de alta o edición. */
public record LineaOrdenCompraDTO(
        @Schema(example = "1")
        @NotNull(message = "Selecciona un producto.")
        @Positive(message = "El producto no es válido.")
        Long productoId,

        @Schema(example = "10")
        @NotNull(message = "Indica la cantidad.")
        @Min(value = 1, message = "La cantidad debe ser al menos 1.")
        @Max(value = 999_999, message = "La cantidad no puede superar 999999.")
        Integer cantidad,

        @Schema(example = "1250.50", description = "Opcional: sin valor se usa el precio actual del producto.")
        @Positive(message = "El costo unitario debe ser mayor que 0.")
        @Digits(integer = 12, fraction = 2, message = "El costo admite hasta 12 enteros y 2 decimales.")
        BigDecimal costoUnitario) {
}
