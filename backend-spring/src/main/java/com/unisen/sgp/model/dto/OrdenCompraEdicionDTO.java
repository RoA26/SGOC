package com.unisen.sgp.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Edición de un borrador: observaciones y líneas completas (el proveedor no cambia). */
public record OrdenCompraEdicionDTO(
        @Size(max = 1000, message = "Las observaciones no pueden superar 1000 caracteres.")
        String observaciones,

        @NotNull(message = "Añade al menos un producto.")
        @Size(min = 1, max = 100, message = "La orden debe tener entre 1 y 100 líneas.")
        List<@Valid @NotNull(message = "Línea vacía.") LineaOrdenCompraDTO> detalles) {

    public OrdenCompraEdicionDTO {
        observaciones = Textos.vacioANull(observaciones);
    }
}
