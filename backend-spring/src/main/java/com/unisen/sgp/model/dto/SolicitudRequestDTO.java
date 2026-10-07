package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.Solicitud;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Alta de una solicitud con sus líneas. No lleva solicitante ni estado: el backend fija el
 * usuario autenticado y PENDIENTE.
 */
public record SolicitudRequestDTO(
        @Schema(example = "Reposición de tornillería para el mantenimiento de la línea 2.")
        @NotBlank(message = "La justificación es obligatoria.")
        @Size(min = 10, max = Solicitud.JUSTIFICACION_MAX_LENGTH,
                message = "La justificación debe tener entre 10 y 1000 caracteres.")
        String justificacion,

        @NotEmpty(message = "Agrega al menos un producto.")
        @Size(max = SolicitudRequestDTO.MAX_LINEAS, message = "La solicitud admite hasta 50 productos.")
        List<@NotNull(message = "La línea está vacía.") @Valid DetalleSolicitudDTO> detalles) {

    public static final int MAX_LINEAS = 50;

    public SolicitudRequestDTO {
        justificacion = Textos.limpiar(justificacion);
    }
}
