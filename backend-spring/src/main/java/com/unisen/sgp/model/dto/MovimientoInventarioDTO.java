package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.DetalleRecepcion;
import com.unisen.sgp.model.entity.MovimientoInventario;
import com.unisen.sgp.model.entity.TipoMovimiento;
import java.math.BigDecimal;
import java.time.Instant;

/** Fila del kárdex. En las recepciones incluye la orden de origen; en ventas y devoluciones, la venta. */
public record MovimientoInventarioDTO(
        Long id,
        OrdenCompraResponseDTO.Producto producto,
        TipoMovimiento tipo,
        int cantidad,
        int saldoResultante,
        BigDecimal costoUnitario,
        String motivo,
        Long recepcionId,
        Long ordenId,
        Long ordenNumero,
        UsuarioResumenDTO usuario,
        Instant fecha,
        /** Venta de origen (salidas por venta y entradas por devolución o anulación). */
        Long ventaId,
        String ventaNumero) {

    /** Debe invocarse dentro de la transacción: recorre asociaciones LAZY (cargadas por lotes). */
    public static MovimientoInventarioDTO from(MovimientoInventario movimiento) {
        DetalleRecepcion linea = movimiento.getDetalleRecepcion();
        var recepcion = linea == null ? null : linea.getRecepcion();
        var orden = recepcion == null ? null : recepcion.getOrden();
        var lineaVenta = movimiento.getDetalleVenta() != null ? movimiento.getDetalleVenta()
                : movimiento.getDetalleDevolucion() != null ? movimiento.getDetalleDevolucion().getDetalleVenta() : null;
        var venta = lineaVenta == null ? null : lineaVenta.getVenta();
        return new MovimientoInventarioDTO(
                movimiento.getId(),
                OrdenCompraResponseDTO.Producto.from(movimiento.getProducto()),
                movimiento.getTipo(),
                movimiento.getCantidad(),
                movimiento.getSaldoResultante(),
                movimiento.getCostoUnitario(),
                movimiento.getMotivo(),
                recepcion == null ? null : recepcion.getId(),
                orden == null ? null : orden.getId(),
                orden == null ? null : orden.getNumero(),
                UsuarioResumenDTO.from(movimiento.getUsuario()),
                movimiento.getFecha(),
                venta == null ? null : venta.getId(),
                venta == null ? null : VentaDTO.numeroVisible(venta.getNumero()));
    }
}
