package com.unisen.sgp.model.entity;

/**
 * Roles de acceso. Se persisten por nombre (columna {@code usuarios.rol}); añadir uno
 * nuevo requiere una migración que amplíe la restricción {@code ck_usuarios_rol}.
 */
public enum Rol {
    ADMIN,
    USUARIO;

    /** Nombre de la autoridad para Spring Security (p. ej. {@code ROLE_ADMIN}). */
    public String authority() {
        return "ROLE_" + name();
    }
}
