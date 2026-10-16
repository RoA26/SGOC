package com.unisen.sgp.model.entity;

/**
 * Origen de un movimiento de inventario. Las ventas del futuro POS añadirán su propio tipo (y
 * su valor en {@code ck_movimientos_inventario_tipo}) en una migración posterior.
 */
public enum TipoMovimiento {
    /** Entrada por una línea de recepción de una orden de compra, con el costo de la orden. */
    RECEPCION,
    /** Corrección manual con motivo: inventario inicial, conteo físico, merma o daño. */
    AJUSTE
}
