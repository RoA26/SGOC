package com.unisen.sgp.model.entity;

/**
 * Ciclo de vida de una solicitud interna. Transiciones válidas: {@code PENDIENTE → APROBADA}
 * y {@code PENDIENTE → RECHAZADA}; los estados revisados son finales.
 */
public enum EstadoSolicitud {
    PENDIENTE("pendiente"),
    APROBADA("aprobada"),
    RECHAZADA("rechazada");

    private final String descripcion;

    EstadoSolicitud(String descripcion) {
        this.descripcion = descripcion;
    }

    /** En minúsculas, para mensajes ("La solicitud ya fue aprobada."). */
    public String descripcion() {
        return descripcion;
    }
}
