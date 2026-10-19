package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.DetalleRecepcion;
import com.unisen.sgp.model.entity.IncidenciaRecepcion;
import com.unisen.sgp.model.entity.Recepcion;
import com.unisen.sgp.model.entity.TipoIncidencia;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Recepción registrada con sus líneas (valoradas al costo pactado en la orden) y sus
 * incidencias. Para el trabajador se envía {@link #sinCostos()}: sin costo unitario ni valor
 * (no los ve en ninguna parte de la aplicación).
 */
public record RecepcionResponseDTO(
        Long id,
        Long ordenId,
        long ordenNumero,
        String proveedor,
        Instant recibidaEn,
        UsuarioResumenDTO recibidaPor,
        String observaciones,
        List<Linea> detalles,
        List<Incidencia> incidencias,
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

        Linea sinCosto() {
            return new Linea(id, detalleOrdenId, producto, cantidad, null);
        }
    }

    /** Novedad de la entrega; {@code producto} y {@code detalleOrdenId} solo si se refiere a una línea. */
    public record Incidencia(Long id, TipoIncidencia tipo, Long detalleOrdenId, OrdenCompraResponseDTO.Producto producto,
                             Integer cantidad, String descripcion) {

        static Incidencia from(IncidenciaRecepcion incidencia) {
            var linea = incidencia.getDetalleOrden();
            return new Incidencia(incidencia.getId(), incidencia.getTipo(), linea == null ? null : linea.getId(),
                    linea == null ? null : OrdenCompraResponseDTO.Producto.from(linea.getProducto()),
                    incidencia.getCantidad(), incidencia.getDescripcion());
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
                recepcion.getIncidencias().stream().map(Incidencia::from).toList(),
                lineas.stream().mapToInt(Linea::cantidad).sum(),
                valor);
    }

    /** La misma recepción sin costos ni valor (vista del trabajador). */
    public RecepcionResponseDTO sinCostos() {
        return new RecepcionResponseDTO(id, ordenId, ordenNumero, proveedor, recibidaEn, recibidaPor, observaciones,
                detalles.stream().map(Linea::sinCosto).toList(), incidencias, unidades, null);
    }

    /** Según quién la pide: los gestores ven costos; el trabajador, no. */
    public RecepcionResponseDTO para(boolean verCostos) {
        return verCostos ? this : sinCostos();
    }
}
