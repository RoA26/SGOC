package com.unisen.sgp.security;

/** Expresiones de {@code @PreAuthorize} reutilizables. */
public final class Permisos {

    /** Solo administradores (p. ej. invitaciones). */
    public static final String ADMIN = "hasRole('ADMIN')";

    /** Gestión: catálogos y revisión de solicitudes. Coincide con {@code Rol.esGestor()}. */
    public static final String GESTION = "hasAnyRole('ADMIN', 'GERENTE')";

    private Permisos() {
    }
}
