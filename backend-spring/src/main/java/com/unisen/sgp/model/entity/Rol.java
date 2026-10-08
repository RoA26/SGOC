package com.unisen.sgp.model.entity;

/**
 * Roles de acceso. Se persisten por nombre (columna {@code usuarios.rol}); añadir uno
 * nuevo requiere una migración que amplíe la restricción {@code ck_usuarios_rol}.
 */
public enum Rol {
    /**
     * Operador de la plataforma SaaS. No pertenece a ninguna empresa: sin cabecera
     * {@code X-Tenant-ID} actúa en modo global; con ella, dentro de esa empresa (soporte).
     */
    SUPER_ADMIN,
    /** Administra su empresa: catálogos, revisión de solicitudes e invitaciones. */
    GERENTE,
    /** Crea solicitudes en su empresa y consulta las suyas y los catálogos. */
    USUARIO;

    /** Gestiona catálogos y revisa solicitudes (en sintonía con {@code Permisos.GESTION}). */
    public boolean esGestor() {
        return this == SUPER_ADMIN || this == GERENTE;
    }

    /** Todos los roles salvo SUPER_ADMIN pertenecen a una empresa, que fija su tenant. */
    public boolean perteneceAEmpresa() {
        return this != SUPER_ADMIN;
    }

    /** Nombre de la autoridad para Spring Security (p. ej. {@code ROLE_ADMIN}). */
    public String authority() {
        return "ROLE_" + name();
    }
}
