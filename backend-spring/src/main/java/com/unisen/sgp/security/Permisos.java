package com.unisen.sgp.security;

/** Expresiones de {@code @PreAuthorize} reutilizables. */
public final class Permisos {

    /** Operación de la plataforma (configuración global, empresas). */
    public static final String SUPER_ADMIN = "hasRole('SUPER_ADMIN')";

    /**
     * Gestión de una empresa: catálogos, trabajadores, invitaciones y revisión de solicitudes.
     * Coincide con {@code Rol.esGestor()}. El SUPER_ADMIN la ejerce solo con un contexto de
     * empresa (los servicios exigen {@code TenantContextHolder.requerirEmpresa()}).
     */
    public static final String GESTION = "hasAnyRole('SUPER_ADMIN', 'ADMIN_EMPRESA')";

    /** Personas de una empresa (no el SUPER_ADMIN): p. ej. crear solicitudes. */
    public static final String MIEMBRO_EMPRESA = "hasAnyRole('ADMIN_EMPRESA', 'TRABAJADOR')";

    private Permisos() {
    }
}
