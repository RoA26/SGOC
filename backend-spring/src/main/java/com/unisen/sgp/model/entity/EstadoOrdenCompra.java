package com.unisen.sgp.model.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida de una orden de compra. Transiciones válidas (las valida el backend):
 *
 * <ul>
 *   <li>{@code BORRADOR → EMITIDA}: se envía al proveedor; ya no se editan sus líneas.</li>
 *   <li>{@code EMITIDA → RECIBIDA_PARCIALMENTE | RECIBIDA}: al registrar recepciones.</li>
 *   <li>{@code RECIBIDA_PARCIALMENTE → RECIBIDA}: cuando llega todo lo pendiente.</li>
 *   <li>{@code BORRADOR | EMITIDA → CANCELADA}: solo sin mercancía recibida.</li>
 * </ul>
 *
 * RECIBIDA y CANCELADA son finales.
 */
public enum EstadoOrdenCompra {
    BORRADOR("en borrador"),
    EMITIDA("emitida"),
    RECIBIDA_PARCIALMENTE("recibida parcialmente"),
    RECIBIDA("recibida"),
    CANCELADA("cancelada");

    /** Órdenes vivas: comprometen productos y proveedores (no se pueden dar de baja). */
    public static final Set<EstadoOrdenCompra> ABIERTAS = EnumSet.of(BORRADOR, EMITIDA, RECIBIDA_PARCIALMENTE);

    private final String descripcion;

    EstadoOrdenCompra(String descripcion) {
        this.descripcion = descripcion;
    }

    /** En minúsculas, para mensajes ("La orden está cancelada."). */
    public String descripcion() {
        return descripcion;
    }

    public boolean admiteRecepcion() {
        return this == EMITIDA || this == RECIBIDA_PARCIALMENTE;
    }
}
