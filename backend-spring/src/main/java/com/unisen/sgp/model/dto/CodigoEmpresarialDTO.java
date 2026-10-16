package com.unisen.sgp.model.dto;

/**
 * Código empresarial propuesto o consultado y si está libre en este momento. La disponibilidad es
 * orientativa: el alta vuelve a comprobarla y la restricción UNIQUE de la BD decide.
 */
public record CodigoEmpresarialDTO(String codigo, boolean disponible) {
}
