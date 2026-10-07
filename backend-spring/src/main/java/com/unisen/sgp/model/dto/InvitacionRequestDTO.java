package com.unisen.sgp.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Parámetros opcionales al generar un código de invitación. */
public record InvitacionRequestDTO(
        @Schema(description = "Horas de validez (1 a 720). Por defecto, 72.", example = "72")
        @Min(value = 1, message = "La validez mínima es de 1 hora.")
        @Max(value = 720, message = "La validez máxima es de 720 horas (30 días).")
        Integer horasValidez) {
}
