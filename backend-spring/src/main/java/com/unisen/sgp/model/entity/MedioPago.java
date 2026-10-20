package com.unisen.sgp.model.entity;

/** Medio de pago registrado en la caja (sin integración con pasarelas). */
public enum MedioPago {
    EFECTIVO,
    TARJETA_DEBITO,
    TARJETA_CREDITO,
    TRANSFERENCIA,
    OTRO
}
