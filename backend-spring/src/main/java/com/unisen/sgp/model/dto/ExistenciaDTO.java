package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.EstadoStock;
import com.unisen.sgp.model.entity.Existencia;
import java.time.Instant;

/** Existencias de un producto activo con su estado frente al stock mínimo. */
public record ExistenciaDTO(
        Long productoId,
        String sku,
        String nombre,
        String codigoBarras,
        int cantidad,
        int stockMinimo,
        EstadoStock estado,
        Instant actualizadoEn) {

    /** Debe invocarse dentro de la transacción (producto LAZY, cargado con la consulta). */
    public static ExistenciaDTO from(Existencia existencia) {
        var producto = existencia.getProducto();
        return new ExistenciaDTO(
                producto.getId(),
                producto.getSku(),
                producto.getNombre(),
                producto.getCodigoBarras(),
                existencia.getCantidad(),
                producto.getStockMinimo(),
                EstadoStock.de(existencia.getCantidad(), producto.getStockMinimo()),
                existencia.getActualizadoEn());
    }
}
