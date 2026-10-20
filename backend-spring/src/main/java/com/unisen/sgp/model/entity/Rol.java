package com.unisen.sgp.model.entity;

/**
 * Roles de acceso. Se persisten por nombre (columna {@code usuarios.rol}); añadir uno
 * nuevo requiere una migración que amplíe la restricción {@code ck_usuarios_rol}.
 * Hasta V8, ADMINISTRADOR_COMERCIAL se llamaba GERENTE y TRABAJADOR, USUARIO; entre V8 y V14,
 * ADMINISTRADOR_COMERCIAL se llamó ADMIN_EMPRESA.
 */
public enum Rol {
    /**
     * Administración global de la plataforma. No pertenece a ninguna empresa: administra las
     * empresas y, con {@code X-Tenant-ID}, consulta los datos de una concreta (supervisión) o de
     * todas en el modo global explícito.
     */
    SUPER_ADMIN,
    /**
     * Administrador comercial de una empresa concreta: trabajadores, invitaciones, catálogos,
     * compras, precios, costos, rentabilidad, descuentos, devoluciones y reportes, siempre dentro
     * de su empresa. Nunca tiene privilegios globales.
     */
    ADMINISTRADOR_COMERCIAL,
    /**
     * Usuario operativo de su empresa: crea solicitudes y consulta las suyas y los catálogos. Si
     * se registra con el código de empresa, queda PENDIENTE hasta que un administrador lo apruebe.
     */
    TRABAJADOR;

    /** Gestiona catálogos y revisa solicitudes (en sintonía con {@code Permisos.GESTION}). */
    public boolean esGestor() {
        return this == SUPER_ADMIN || this == ADMINISTRADOR_COMERCIAL;
    }

    /** Todos los roles salvo SUPER_ADMIN pertenecen a una empresa, que fija su tenant. */
    public boolean perteneceAEmpresa() {
        return this != SUPER_ADMIN;
    }

    /** Nombre de la autoridad para Spring Security (p. ej. {@code ROLE_ADMINISTRADOR_COMERCIAL}). */
    public String authority() {
        return "ROLE_" + name();
    }
}
