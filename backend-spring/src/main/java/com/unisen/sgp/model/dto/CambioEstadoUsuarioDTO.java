package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.EstadoUsuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Decisión de un gestor sobre un trabajador: aprobar (ACTIVO), rechazar (RECHAZADO),
 * desactivar (INACTIVO) o reactivar (ACTIVO). Las transiciones válidas están en
 * {@link EstadoUsuario#puedePasarA}.
 */
public record CambioEstadoUsuarioDTO(
        @Schema(example = "ACTIVO", allowableValues = {"ACTIVO", "RECHAZADO", "INACTIVO"})
        @NotNull(message = "El estado es obligatorio.")
        EstadoUsuario estado) {
}
