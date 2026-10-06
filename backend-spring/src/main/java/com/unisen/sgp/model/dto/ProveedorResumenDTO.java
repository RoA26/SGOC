package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.Proveedor;

/** Datos mínimos del proveedor embebidos en otras respuestas (p. ej. productos). */
public record ProveedorResumenDTO(Long id, String nit, String razonSocial) {

    public static ProveedorResumenDTO from(Proveedor proveedor) {
        return new ProveedorResumenDTO(proveedor.getId(), proveedor.getNit(), proveedor.getRazonSocial());
    }
}
