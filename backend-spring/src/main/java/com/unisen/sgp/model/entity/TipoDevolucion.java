package com.unisen.sgp.model.entity;

/** Registro que revierte una venta, total o parcialmente. */
public enum TipoDevolucion {
    /** Devolución de unidades (parcial o total), en cualquier fecha posterior a la venta. */
    DEVOLUCION,
    /** Anulación de una venta sin devoluciones, el mismo día: corrige un error de registro. */
    ANULACION
}
