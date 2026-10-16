package com.unisen.sgp.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Activar o desactivar una empresa (solo SUPER_ADMIN). Desactivarla bloquea a todos sus usuarios
 * desde su siguiente petición (403, motivo EMPRESA_INACTIVA); reactivarla les devuelve el acceso.
 */
public record CambioEstadoEmpresaDTO(
        @Schema(example = "false")
        @NotNull(message = "Indica si la empresa queda activa.")
        Boolean activa) {
}
