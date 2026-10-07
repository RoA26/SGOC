package com.unisen.sgp.model.entity;

/**
 * Roles de acceso. Se persisten por nombre (columna {@code usuarios.rol}); añadir uno
 * nuevo requiere una migración que amplíe la restricción {@code ck_usuarios_rol}.
 */
public enum Rol {
    /** Todo: catálogos, revisión de solicitudes e invitaciones. */
    ADMIN,
    /** Mantiene catálogos y revisa solicitudes; no gestiona invitaciones. */
    GERENTE,
    /** Crea solicitudes y consulta las suyas y los catálogos. */
    USUARIO;

    /** Ve todas las solicitudes y puede aprobarlas o rechazarlas (en sintonía con {@code Permisos.GESTION}). */
    public boolean esGestor() {
        return this == ADMIN || this == GERENTE;
    }

    /** Nombre de la autoridad para Spring Security (p. ej. {@code ROLE_ADMIN}). */
    public String authority() {
        return "ROLE_" + name();
    }
}
