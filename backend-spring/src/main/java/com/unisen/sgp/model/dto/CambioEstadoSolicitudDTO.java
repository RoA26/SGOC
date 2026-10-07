package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.EstadoSolicitud;
import com.unisen.sgp.model.entity.Solicitud;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Revisión de una solicitud pendiente. El comentario es obligatorio al rechazar. */
public record CambioEstadoSolicitudDTO(
        @Schema(example = "APROBADA", allowableValues = {"APROBADA", "RECHAZADA"})
        @NotNull(message = "El estado es obligatorio.")
        EstadoSolicitud estado,

        @Schema(example = "Aprobada para el presupuesto de octubre.")
        @Size(max = Solicitud.COMENTARIO_MAX_LENGTH, message = "El comentario no puede superar 500 caracteres.")
        String comentario) {

    public CambioEstadoSolicitudDTO {
        comentario = Textos.vacioANull(comentario);
    }
}
