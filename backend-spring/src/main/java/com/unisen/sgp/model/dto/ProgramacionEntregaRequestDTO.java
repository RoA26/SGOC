package com.unisen.sgp.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Programar o reprogramar la entrega de una orden. La franja es opcional (ambas horas o
 * ninguna). El motivo es obligatorio al reprogramar (lo valida el servicio, que sabe si ya
 * existía una programación).
 */
public record ProgramacionEntregaRequestDTO(
        @Schema(example = "2026-11-03")
        @NotNull(message = "Indica la fecha estimada de llegada.")
        LocalDate fechaEstimada,

        @Schema(example = "08:00", type = "string")
        @JsonFormat(pattern = "HH:mm")
        LocalTime horaDesde,

        @Schema(example = "12:00", type = "string")
        @JsonFormat(pattern = "HH:mm")
        LocalTime horaHasta,

        @Schema(description = "El proveedor confirmó la fecha (si no, es una estimación interna)")
        Boolean confirmadaProveedor,

        @Size(max = 60, message = "La referencia no puede superar 60 caracteres.")
        String referenciaProveedor,

        @Size(max = 500, message = "Las observaciones no pueden superar 500 caracteres.")
        String observaciones,

        @Size(max = 300, message = "El motivo no puede superar 300 caracteres.")
        String motivo) {

    public ProgramacionEntregaRequestDTO {
        referenciaProveedor = Textos.vacioANull(referenciaProveedor);
        observaciones = Textos.vacioANull(observaciones);
        motivo = Textos.vacioANull(motivo);
    }
}
