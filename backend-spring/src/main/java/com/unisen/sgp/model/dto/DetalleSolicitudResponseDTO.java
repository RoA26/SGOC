package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.DetalleSolicitud;
import com.unisen.sgp.model.entity.ProductoReferencia;
import java.math.BigDecimal;

/** Línea de una solicitud con el producto y su importe orientativo. */
public record DetalleSolicitudResponseDTO(
        Long id,
        ProductoLinea producto,
        int cantidad,
        BigDecimal subtotalEstimado) {

    /**
     * Producto tal como está hoy en el catálogo: el precio es el actual, no uno pactado, y
     * {@code activo = false} indica que se dio de baja después de solicitarlo.
     */
    public record ProductoLinea(Long id, String sku, String nombre, BigDecimal precio, boolean activo) {

        static ProductoLinea from(ProductoReferencia producto) {
            return new ProductoLinea(producto.getId(), producto.getSku(), producto.getNombre(), producto.getPrecio(),
                    producto.isActivo());
        }
    }

    public static DetalleSolicitudResponseDTO from(DetalleSolicitud detalle) {
        return new DetalleSolicitudResponseDTO(
                detalle.getId(),
                ProductoLinea.from(detalle.getProducto()),
                detalle.getCantidad(),
                detalle.subtotalEstimado());
    }
}
