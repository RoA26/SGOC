package com.unisen.sgp.exception;

/**
 * El código de invitación no permite registrarse. Se responde 400 con
 * {@code errors.codigoInvitacion}, igual que cualquier otro campo inválido.
 */
public class InvitacionInvalidaException extends CampoInvalidoException {

    public static final String CAMPO = "codigoInvitacion";

    public enum Motivo {
        NO_EXISTE("El código de invitación no es válido."),
        USADO("El código de invitación ya fue utilizado."),
        CADUCADO("El código de invitación ha caducado. Solicita uno nuevo al administrador."),
        EMPRESA_INACTIVA("La empresa de esta invitación no está activa.");

        private final String mensaje;

        Motivo(String mensaje) {
            this.mensaje = mensaje;
        }
    }

    private final Motivo motivo;

    public InvitacionInvalidaException(Motivo motivo) {
        super(CAMPO, motivo.mensaje);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
