package com.unisen.sgp.security;

import com.unisen.sgp.config.LimitesProperties;
import com.unisen.sgp.exception.DemasiadosIntentosException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

/**
 * Límites de intentos del login y del registro (capa A de la auditoría): por usuario y por código
 * de empresa, <strong>nunca por IP</strong> (detrás de Nginx la IP del cliente no es fiable, ver
 * {@code docs/auditoria-sgp.md}).
 *
 * <ul>
 *   <li>Login: solo cuentan las credenciales incorrectas ({@link BadCredentialsException}), que el
 *       sistema da igual exista o no el usuario; un login correcto o un 403 por estado de la cuenta
 *       (contraseña correcta) no cuentan. La clave es el username ya normalizado por
 *       {@code LoginRequest}, el mismo con el que se busca la cuenta.</li>
 *   <li>Registro: cuenta cada intento con un código de empresa (normalizado con
 *       {@link CodigosInvitacion#normalizar}, el mismo criterio con el que se busca) y, además,
 *       cada intento de alta de cualquier tipo en un total global.</li>
 * </ul>
 *
 * Se comprueba antes de la operación (sin coste de BCrypt ni transacción) y el exceso responde 429
 * con {@code Retry-After}. En memoria: se vacía al reiniciar y no se coordina entre instancias.
 */
@Component
public class LimitadorAcceso {

    private static final Logger log = LoggerFactory.getLogger(LimitadorAcceso.class);
    private static final String CLAVE_GLOBAL = "*";

    private final LimiteDeIntentos loginPorUsuario;
    private final LimiteDeIntentos registroPorCodigo;
    private final LimiteDeIntentos registroGlobal;

    public LimitadorAcceso(LimitesProperties limites, Clock clock) {
        this.loginPorUsuario = new LimiteDeIntentos(limites.login().maxFallos(), limites.login().ventana(),
                limites.maxClaves(), clock);
        this.registroPorCodigo = new LimiteDeIntentos(limites.registro().maxPorCodigo(),
                limites.registro().ventanaPorCodigo(), limites.maxClaves(), clock);
        this.registroGlobal = new LimiteDeIntentos(limites.registro().maxGlobal(), limites.registro().ventanaGlobal(),
                1, clock);
    }

    /**
     * Ejecuta el login si el usuario no ha agotado sus intentos fallidos.
     *
     * @param username username ya normalizado (como lo deja {@code LoginRequest})
     * @throws DemasiadosIntentosException si se alcanzó el límite (no se comprueba la contraseña)
     */
    public <T> T login(String username, Supplier<T> accion) {
        String clave = username == null ? "" : username;
        Optional<Duration> espera = loginPorUsuario.reservar(clave);
        if (espera.isPresent()) {
            throw new DemasiadosIntentosException(
                    "Demasiados intentos fallidos con este usuario. Espera " + legible(espera.get())
                            + " e inténtalo de nuevo.", espera.get());
        }
        boolean fallo = false;
        try {
            return accion.get();
        } catch (BadCredentialsException ex) {
            fallo = true;
            throw ex;
        } finally {
            if (loginPorUsuario.liberar(clave, fallo)) {
                log.warn("Límite de intentos de login alcanzado para el usuario #{} ({} fallos en {}).",
                        huella(clave), loginPorUsuario.maximo(), loginPorUsuario.ventana());
            }
        }
    }

    /**
     * Ejecuta un alta si no se agotó el total global ni, con código de empresa, el de ese código.
     *
     * @param codigoEmpresa el código tal como llega (null si el alta es con invitación)
     * @throws DemasiadosIntentosException si se alcanzó alguno de los dos límites
     */
    public <T> T registro(String codigoEmpresa, Supplier<T> accion) {
        Optional<Duration> esperaGlobal = registroGlobal.reservar(CLAVE_GLOBAL);
        if (esperaGlobal.isPresent()) {
            throw new DemasiadosIntentosException("Hay demasiados registros en este momento. Espera "
                    + legible(esperaGlobal.get()) + " e inténtalo de nuevo.", esperaGlobal.get());
        }
        String clave = codigoEmpresa == null ? null : CodigosInvitacion.normalizar(codigoEmpresa);
        if (clave != null) {
            Optional<Duration> espera = registroPorCodigo.reservar(clave);
            if (espera.isPresent()) {
                registroGlobal.liberar(CLAVE_GLOBAL, false);
                throw new DemasiadosIntentosException("Demasiados intentos de registro con este código. Espera "
                        + legible(espera.get()) + " e inténtalo de nuevo.", espera.get());
            }
        }
        try {
            return accion.get();
        } finally {
            if (registroGlobal.liberar(CLAVE_GLOBAL, true)) {
                log.warn("Límite global de registros alcanzado ({} en {}).", registroGlobal.maximo(),
                        registroGlobal.ventana());
            }
            if (clave != null && registroPorCodigo.liberar(clave, true)) {
                // Nunca el código: solo una huella para correlacionar avisos.
                log.warn("Límite de registros alcanzado para el código de empresa #{} ({} en {}).", huella(clave),
                        registroPorCodigo.maximo(), registroPorCodigo.ventana());
            }
        }
    }

    /** Fallos vigentes de un usuario (pruebas y diagnóstico). */
    public int fallosDeLogin(String username) {
        return loginPorUsuario.contados(username);
    }

    /** Olvida todos los intentos (pruebas automatizadas). */
    public void reiniciar() {
        loginPorUsuario.reiniciar();
        registroPorCodigo.reiniciar();
        registroGlobal.reiniciar();
    }

    /** "15 minutos", "1 minuto", "30 segundos". */
    static String legible(Duration espera) {
        long segundos = DemasiadosIntentosException.segundos(espera);
        if (segundos < 60) {
            return segundos + (segundos == 1 ? " segundo" : " segundos");
        }
        long minutos = (segundos + 59) / 60;
        return minutos + (minutos == 1 ? " minuto" : " minutos");
    }

    /** Primeros 8 caracteres del SHA-256: permite correlacionar avisos sin exponer el dato. */
    private static String huella(String valor) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 4);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
