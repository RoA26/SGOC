package com.unisen.sgp.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Recepción (parcial o total) de una orden emitida. La clave de idempotencia la genera el
 * cliente una vez por intento de registro (p. ej. un UUID): si la petición se repite con la
 * misma clave, se devuelve la recepción ya registrada en lugar de duplicar la mercancía.
 */
public record RecepcionRequestDTO(
        @Schema(example = "3f1c9a52-6a2e-4c1b-9a7e-0f6d1b2c3d4e")
        @NotBlank(message = "Falta la clave de idempotencia.")
        @Pattern(regexp = Patrones.CLAVE_IDEMPOTENCIA,
                message = "La clave de idempotencia admite de 8 a 64 letras, números o guiones.")
        String claveIdempotencia,

        @Size(max = 500, message = "Las observaciones no pueden superar 500 caracteres.")
        String observaciones,

        @NotNull(message = "Indica al menos una línea recibida.")
        @Size(min = 1, max = 100, message = "La recepción debe tener entre 1 y 100 líneas.")
        List<@Valid @NotNull(message = "Línea vacía.") LineaRecepcionDTO> detalles) {

    public RecepcionRequestDTO {
        claveIdempotencia = Textos.limpiar(claveIdempotencia);
        observaciones = Textos.vacioANull(observaciones);
    }
}
