package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.TipoIncidencia;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Novedad informada al recibir. FALTANTE, DANADO y EXCEDENTE: línea de la orden y cantidad.
 * PRODUCTO_INCORRECTO y OTRO: descripción (línea y cantidad opcionales). Ninguna entra al
 * inventario.
 */
public record IncidenciaRecepcionDTO(
        @Schema(example = "DANADO")
        @NotNull(message = "Indica el tipo de incidencia.")
        TipoIncidencia tipo,

        @Schema(example = "1", description = "Línea de la orden afectada (detalles[].id)")
        @Positive(message = "La línea no es válida.")
        Long detalleOrdenId,

        @Schema(example = "2")
        @Min(value = 1, message = "La cantidad debe ser al menos 1.")
        @Max(value = 999_999, message = "La cantidad no puede superar 999999.")
        Integer cantidad,

        @Size(max = 500, message = "La descripción no puede superar 500 caracteres.")
        String descripcion) {

    public IncidenciaRecepcionDTO {
        descripcion = Textos.vacioANull(descripcion);
    }
}
