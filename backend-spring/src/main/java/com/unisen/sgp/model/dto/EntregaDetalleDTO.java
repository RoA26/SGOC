package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.DetalleOrdenCompra;
import java.util.List;

/**
 * Lo que necesita quien recibe la mercancía: la entrega, las líneas con lo pedido, lo recibido y
 * lo pendiente (sin costos), las recepciones anteriores y el historial de la programación.
 */
public record EntregaDetalleDTO(
        EntregaDTO entrega,
        String observacionesOrden,
        List<Linea> lineas,
        List<RecepcionResponseDTO> recepciones,
        List<ProgramacionEntregaDTO> programaciones) {

    public record Linea(Long id, OrdenCompraResponseDTO.Producto producto, int cantidad, int cantidadRecibida,
                        int pendiente) {

        public static Linea from(DetalleOrdenCompra detalle) {
            return new Linea(detalle.getId(), OrdenCompraResponseDTO.Producto.from(detalle.getProducto()),
                    detalle.getCantidad(), detalle.getCantidadRecibida(), detalle.pendiente());
        }
    }
}
