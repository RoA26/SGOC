package com.unisen.sgp.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Alta de una orden de compra (nace en BORRADOR). La empresa, el número, el autor y el estado
 * los fija el backend: el cliente no puede elegirlos.
 */
public record OrdenCompraRequestDTO(
        @Schema(example = "1")
        @NotNull(message = "Selecciona el proveedor.")
        @Positive(message = "El proveedor no es válido.")
        Long proveedorId,

        @Size(max = 1000, message = "Las observaciones no pueden superar 1000 caracteres.")
        String observaciones,

        @NotNull(message = "Añade al menos un producto.")
        @Size(min = 1, max = 100, message = "La orden debe tener entre 1 y 100 líneas.")
        List<@Valid @NotNull(message = "Línea vacía.") LineaOrdenCompraDTO> detalles) {

    public OrdenCompraRequestDTO {
        observaciones = Textos.vacioANull(observaciones);
    }
}
