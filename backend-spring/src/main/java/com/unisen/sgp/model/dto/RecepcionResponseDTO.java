package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.DetalleRecepcion;
import com.unisen.sgp.model.entity.Recepcion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Recepción registrada con sus líneas, valoradas al costo pactado en la orden. */
public record RecepcionResponseDTO(
        Long id,
        Long ordenId,
        long ordenNumero,
        String proveedor,
        Instant recibidaEn,
        UsuarioResumenDTO recibidaPor,
        String observaciones,
        List<Linea> detalles,
        int unidades,
        BigDecimal valor) {

    public record Linea(Long id, Long detalleOrdenId, OrdenCompraResponseDTO.Producto producto, int cantidad,
                        BigDecimal costoUnitario) {

        static Linea from(DetalleRecepcion detalle) {
            var lineaOrden = detalle.getDetalleOrden();
            return new Linea(detalle.getId(), lineaOrden.getId(),
                    OrdenCompraResponseDTO.Producto.from(lineaOrden.getProducto()), detalle.getCantidad(),
                    lineaOrden.getCostoUnitario());
        }
    }

    /** Debe invocarse dentro de la transacción: recorre asociaciones LAZY. */
    public static RecepcionResponseDTO from(Recepcion recepcion) {
        List<Linea> lineas = recepcion.getDetalles().stream().map(Linea::from).toList();
        BigDecimal valor = lineas.stream()
                .map(l -> l.costoUnitario().multiply(BigDecimal.valueOf(l.cantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var orden = recepcion.getOrden();
        return new RecepcionResponseDTO(
                recepcion.getId(),
                orden.getId(),
                orden.getNumero(),
                orden.getProveedor().getRazonSocial(),
                recepcion.getRecibidaEn(),
                UsuarioResumenDTO.from(recepcion.getRecibidaPor()),
                recepcion.getObservaciones(),
                lineas,
                lineas.stream().mapToInt(Linea::cantidad).sum(),
                valor);
    }
}
