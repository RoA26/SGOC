package com.unisen.sgp.model.entity;

/** Origen de un movimiento de inventario (ck_movimientos_inventario_tipo). */
public enum TipoMovimiento {
    /** Entrada por una línea de recepción de una orden de compra, con el costo de la orden. */
    RECEPCION,
    /** Corrección manual con motivo: inventario inicial, conteo físico, merma o daño. */
    AJUSTE,
    /** Salida por una línea de venta, con el costo promedio aplicado (o sin costo si se desconoce). */
    VENTA,
    /** Entrada por una devolución o anulación de venta, con el costo histórico de la línea vendida. */
    DEVOLUCION_VENTA
}
