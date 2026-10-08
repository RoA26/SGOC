package com.unisen.sgp.security;

import com.unisen.sgp.model.entity.EstadoUsuario;
import java.util.Optional;
import org.springframework.security.authentication.AccountStatusException;

/**
 * La identidad está verificada (contraseña correcta o JWT válido) pero la cuenta no puede
 * operar según la BD: está pendiente, rechazada, inactiva o su empresa está desactivada.
 * Siempre se responde 403 con la propiedad {@code motivo}, en el login y en cada petición.
 *
 * <p>El JWT no se "revoca": sigue siendo criptográficamente válido, pero la autorización
 * actual del usuario, que se lee de la BD en cada petición, ya no lo permite.
 */
public class CuentaNoAutorizadaException extends AccountStatusException {

    /** Propiedad del Problem Details con el {@link Motivo} (para que el cliente reaccione). */
    public static final String PROPIEDAD_MOTIVO = "motivo";
    public static final String TITULO = "Cuenta no autorizada";

    public enum Motivo {
        PENDIENTE("Tu cuenta está pendiente de aprobación por el gerente de tu empresa."),
        RECHAZADO("Tu solicitud de acceso fue rechazada. Contacta con el gerente de tu empresa."),
        INACTIVO("La cuenta de usuario está deshabilitada."),
        EMPRESA_INACTIVA("La empresa del usuario está desactivada.");

        private final String mensaje;

        Motivo(String mensaje) {
            this.mensaje = mensaje;
        }

        public String mensaje() {
            return mensaje;
        }
    }

    private final Motivo motivo;

    public CuentaNoAutorizadaException(Motivo motivo) {
        super(motivo.mensaje());
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }

    /** Por qué no puede operar el usuario, o vacío si está autorizado. Una empresa desactivada bloquea a todos. */
    public static Optional<Motivo> motivoDe(UsuarioPrincipal usuario) {
        if (!usuario.isEmpresaActiva()) {
            return Optional.of(Motivo.EMPRESA_INACTIVA);
        }
        EstadoUsuario estado = usuario.getEstado();
        return switch (estado) {
            case ACTIVO -> Optional.empty();
            case PENDIENTE -> Optional.of(Motivo.PENDIENTE);
            case RECHAZADO -> Optional.of(Motivo.RECHAZADO);
            case INACTIVO -> Optional.of(Motivo.INACTIVO);
        };
    }

    /** @throws CuentaNoAutorizadaException si el usuario no puede operar */
    public static void verificar(UsuarioPrincipal usuario) {
        motivoDe(usuario).ifPresent(motivo -> {
            throw new CuentaNoAutorizadaException(motivo);
        });
    }
}
