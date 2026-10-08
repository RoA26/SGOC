package com.unisen.sgp.model.entity;

import java.util.Set;

/**
 * Situación de una cuenta. Se persiste por nombre (columna {@code usuarios.estado},
 * restricción {@code ck_usuarios_estado}). Es la fuente de verdad de si un usuario puede
 * operar: se consulta en la BD en cada petición, no en el JWT.
 *
 * <pre>
 * PENDIENTE ─ aprobar ──→ ACTIVO ←─ reactivar ─ INACTIVO
 *     └──── rechazar ──→ RECHAZADO        ACTIVO ─ desactivar ─→ INACTIVO
 * RECHAZADO ─ aprobar (corregir un rechazo) ─→ ACTIVO
 * </pre>
 */
public enum EstadoUsuario {
    /** Se registró con el código de su empresa y espera la aprobación del gerente. */
    PENDIENTE,
    /** Autorizado: puede iniciar sesión y operar. */
    ACTIVO,
    /** El gerente no aprobó su registro. */
    RECHAZADO,
    /** Estuvo activo y perdió la autorización (p. ej. dejó la empresa). */
    INACTIVO;

    /** Solo una cuenta ACTIVA tiene acceso operativo. */
    public boolean permiteAcceso() {
        return this == ACTIVO;
    }

    /** Transiciones que puede hacer un gestor; PENDIENTE solo se alcanza al registrarse. */
    public boolean puedePasarA(EstadoUsuario destino) {
        return switch (this) {
            case PENDIENTE -> Set.of(ACTIVO, RECHAZADO).contains(destino);
            case ACTIVO -> destino == INACTIVO;
            case INACTIVO, RECHAZADO -> destino == ACTIVO;
        };
    }
}
