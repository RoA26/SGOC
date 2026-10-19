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

    /**
     * Operaciones de compras e inventario que mueven mercancía o compromisos con proveedores
     * (órdenes, recepciones, ajustes): solo el administrador de la empresa. El SUPER_ADMIN las
     * consulta en supervisión, pero no las ejecuta (las FK compuestas de autoría lo exigen
     * también en la BD).
     */
    public static final String ADMIN_EMPRESA = "hasRole('ADMIN_EMPRESA')";

    /**
     * Personas de una empresa (no el SUPER_ADMIN): crear solicitudes y registrar la recepción
     * operativa de mercancía (el trabajador recibe; el administrador también puede hacerlo).
     */
    public static final String MIEMBRO_EMPRESA = "hasAnyRole('ADMIN_EMPRESA', 'TRABAJADOR')";

    /**
     * Lectura operativa sin costos (entregas pendientes, calendario, detalle de recepciones):
     * toda la empresa. El SUPER_ADMIN, solo dentro de una empresa (supervisión).
     */
    public static final String LECTURA_OPERATIVA = "hasAnyRole('SUPER_ADMIN', 'ADMIN_EMPRESA', 'TRABAJADOR')";

    private Permisos() {
    }
}
