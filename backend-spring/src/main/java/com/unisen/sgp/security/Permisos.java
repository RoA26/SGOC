package com.unisen.sgp.security;

/** Expresiones de {@code @PreAuthorize} reutilizables. */
public final class Permisos {

    /** Altas, cambios y bajas de catálogos: solo administradores. */
    public static final String ADMIN = "hasRole('ADMIN')";

    private Permisos() {
    }
}
