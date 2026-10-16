package com.unisen.sgp.model.entity;

/**
 * Situación de un código de invitación en un instante dado. No se persiste: se deriva de
 * {@code usado}, {@code fecha_revocacion} y {@code fecha_expiracion} (ver {@link CodigoInvitacion#estado}).
 */
public enum EstadoInvitacion {
    /** Se puede canjear. */
    VIGENTE,
    /** Ya dio de alta una cuenta. */
    USADA,
    /** Un administrador la anuló antes de usarse. */
    REVOCADA,
    /** Pasó su fecha de expiración sin usarse. */
    CADUCADA
}
