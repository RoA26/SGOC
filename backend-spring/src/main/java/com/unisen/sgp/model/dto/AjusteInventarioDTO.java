package com.unisen.sgp.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Ajuste manual de existencias: inventario inicial, conteo físico, merma. La cantidad lleva
 * signo (positiva entra, negativa sale) y el motivo es obligatorio: queda en el kárdex.
 */
public record AjusteInventarioDTO(
        @Schema(example = "1")
        @NotNull(message = "Selecciona un producto.")
        @Positive(message = "El producto no es válido.")
        Long productoId,

        @Schema(example = "25", description = "Con signo: positiva entra, negativa sale. Nunca 0.")
        @NotNull(message = "Indica la cantidad.")
        @Min(value = -999_999, message = "La cantidad no puede ser inferior a -999999.")
        @Max(value = 999_999, message = "La cantidad no puede superar 999999.")
        Integer cantidad,

        @Schema(example = "Inventario inicial")
        @NotBlank(message = "Indica el motivo del ajuste.")
        @Size(max = 300, message = "El motivo no puede superar 300 caracteres.")
        String motivo,

        @Schema(example = "1250.50", description = "Opcional, solo en entradas: costo unitario de las unidades que entran.")
        @Positive(message = "El costo unitario debe ser mayor que 0.")
        @Digits(integer = 12, fraction = 2, message = "El costo admite hasta 12 enteros y 2 decimales.")
        BigDecimal costoUnitario,

        @Schema(example = "8b0e5f0c-2f5e-4a8e-9a77-1f2d3c4b5a69")
        @NotBlank(message = "Falta la clave de idempotencia.")
        @Pattern(regexp = Patrones.CLAVE_IDEMPOTENCIA,
                message = "La clave de idempotencia admite de 8 a 64 letras, números o guiones.")
        String claveIdempotencia) {

    public AjusteInventarioDTO {
        motivo = Textos.vacioANull(motivo);
        claveIdempotencia = Textos.limpiar(claveIdempotencia);
    }
}
