package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.Producto;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductoResponseDTO(
        Long id,
        String sku,
        String nombre,
        String descripcion,
        BigDecimal precio,
        ProveedorResumenDTO proveedor,
        Instant creadoEn,
        Instant actualizadoEn) {

    /** Debe invocarse dentro de la transacción: accede al proveedor (asociación LAZY). */
    public static ProductoResponseDTO from(Producto producto) {
        return new ProductoResponseDTO(
                producto.getId(),
                producto.getSku(),
                producto.getNombre(),
                producto.getDescripcion(),
                producto.getPrecio(),
                ProveedorResumenDTO.from(producto.getProveedor()),
                producto.getCreadoEn(),
                producto.getActualizadoEn());
    }
}
