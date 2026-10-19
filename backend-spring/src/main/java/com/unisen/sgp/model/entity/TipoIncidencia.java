package com.unisen.sgp.model.entity;

/**
 * Novedad de una entrega. Ninguna mueve existencias: solo lo aceptado (las líneas de la
 * recepción) entra al inventario.
 */
public enum TipoIncidencia {
    /** Unidades que el proveedor debía traer en esta entrega y no llegaron (siguen pendientes). */
    FALTANTE(true),
    /** Unidades llegadas en mal estado y rechazadas (siguen pendientes). */
    DANADO(true),
    /**
     * Unidades de más sobre lo pendiente. No se aceptan en el inventario: recibir de más no tiene
     * una regla aprobada (ver docs/auditoria-sgp.md).
     */
    EXCEDENTE(true),
    /** Llegó un producto que no es el pedido (descripción obligatoria). */
    PRODUCTO_INCORRECTO(false),
    /** Otra novedad (descripción obligatoria). */
    OTRO(false);

    private final boolean porLinea;

    TipoIncidencia(boolean porLinea) {
        this.porLinea = porLinea;
    }

    /** true: se refiere a una línea de la orden y lleva cantidad. false: lleva descripción. */
    public boolean porLinea() {
        return porLinea;
    }
}
