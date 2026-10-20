package com.unisen.sgp.model.entity;

/** Estado de una venta. Nunca se borra: las devoluciones y la anulación dejan su propio registro. */
public enum EstadoVenta {
    CONFIRMADA,
    DEVUELTA_PARCIALMENTE,
    /** Todas las unidades se devolvieron (en una o varias devoluciones). */
    DEVUELTA,
    /** Anulada el mismo día de la venta: deja de contarse en los reportes. */
    ANULADA
}
