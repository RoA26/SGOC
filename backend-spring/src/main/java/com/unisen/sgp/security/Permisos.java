package com.unisen.sgp.security;

/** Expresiones de {@code @PreAuthorize} reutilizables. */
public final class Permisos {

    /** Operación de la plataforma (configuración global, empresas). */
    public static final String SUPER_ADMIN = "hasRole('SUPER_ADMIN')";

    /** Gestión: catálogos, invitaciones y revisión de solicitudes. Coincide con {@code Rol.esGestor()}. */
    public static final String GESTION = "hasAnyRole('SUPER_ADMIN', 'GERENTE')";

    /** Personas de una empresa (no el SUPER_ADMIN): p. ej. crear solicitudes. */
    public static final String MIEMBRO_EMPRESA = "hasAnyRole('GERENTE', 'USUARIO')";

    private Permisos() {
    }
}
