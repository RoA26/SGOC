package com.unisen.sgp.model.entity;

/** Qué hacer cuando la base de una línea queda por debajo de su costo histórico. */
public enum VentaBajoCosto {
    /** Se permite y se advierte (valor por defecto: no hay política aprobada que la bloquee). */
    PERMITIR,
    /** Exige una autorización de excepción del administrador comercial. */
    AUTORIZACION
}
