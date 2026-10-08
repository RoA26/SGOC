package com.unisen.sgp.service;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.exception.DatoDuplicadoException;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    public static final int PASSWORD_MIN_LENGTH = 8;
    /** BCrypt solo procesa los primeros 72 bytes: más allá, dos contraseñas distintas colisionarían. */
    public static final int PASSWORD_MAX_BYTES = 72;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Da de alta un usuario con la contraseña hasheada con BCrypt.
     *
     * <p>Se une a la transacción del llamador: si algo falla después (p. ej. al marcar la
     * invitación), el alta se deshace.
     *
     * <p>Username y correo son únicos en toda la plataforma (no por empresa): identifican a
     * la persona en el login, antes de conocer su empresa.
     *
     * @param empresa empresa del usuario; null solo para SUPER_ADMIN
     * @throws DatoDuplicadoException si el username o el correo ya están en uso
     * @throws CampoInvalidoException si la contraseña no cumple la política
     */
    @Transactional
    public Usuario crearUsuario(String username, String email, String nombre, String password, Rol rol,
                                Empresa empresa) {
        validarPassword(password);
        String usernameNormalizado = Usuario.normalizarUsername(username);
        String emailNormalizado = Usuario.normalizarEmail(email);
        if (usuarioRepository.existsByUsername(usernameNormalizado)) {
            throw DatoDuplicadoException.username();
        }
        if (usuarioRepository.existsByEmail(emailNormalizado)) {
            throw DatoDuplicadoException.email();
        }
        Usuario usuario = new Usuario(usernameNormalizado, emailNormalizado,
                passwordEncoder.encode(password), nombre, rol, empresa);
        // flush: si una alta concurrente se adelanta, la violación UNIQUE salta aquí
        // (→ 409 en GlobalExceptionHandler) y no al confirmar, fuera del servicio.
        return usuarioRepository.saveAndFlush(usuario);
    }

    private static void validarPassword(String password) {
        if (password == null || password.length() < PASSWORD_MIN_LENGTH) {
            throw new CampoInvalidoException("password",
                    "La contraseña debe tener al menos " + PASSWORD_MIN_LENGTH + " caracteres.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw new CampoInvalidoException("password",
                    "La contraseña no puede superar " + PASSWORD_MAX_BYTES + " bytes.");
        }
    }
}
