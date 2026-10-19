package com.unisen.sgp.model.entity;

import java.time.LocalDate;

/**
 * Situación logística de la entrega de una orden, derivada (no se guarda) de su estado, su
 * programación vigente y la fecha de hoy en la zona del negocio.
 */
public enum SituacionEntrega {
    /** Orden en borrador: aún no se ha enviado al proveedor. */
    BORRADOR,
    /** Emitida (o recibida en parte) sin fecha estimada. */
    SIN_PROGRAMAR,
    /** Con fecha estimada de hoy en adelante. */
    PROGRAMADA,
    /** Con fecha estimada anterior a hoy y unidades pendientes. */
    RETRASADA,
    /** Todo recibido. */
    COMPLETA,
    CANCELADA;

    public static SituacionEntrega de(OrdenCompra orden, LocalDate hoy) {
        return switch (orden.getEstado()) {
            case BORRADOR -> BORRADOR;
            case CANCELADA -> CANCELADA;
            case RECIBIDA -> COMPLETA;
            case EMITIDA, RECIBIDA_PARCIALMENTE -> {
                ProgramacionEntrega programacion = orden.getProgramacionEntrega();
                if (programacion == null) {
                    yield SIN_PROGRAMAR;
                }
                yield programacion.getFechaEstimada().isBefore(hoy) ? RETRASADA : PROGRAMADA;
            }
        };
    }
}
