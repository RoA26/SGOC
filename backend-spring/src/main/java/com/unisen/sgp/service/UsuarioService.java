package com.unisen.sgp.service;

import com.unisen.sgp.exception.EmailYaRegistradoException;
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
     * Da de alta un usuario con la contraseña hasheada.
     *
     * @throws EmailYaRegistradoException si el correo ya está en uso
     * @throws IllegalArgumentException   si la contraseña no cumple la política
     */
    @Transactional
    public Usuario crearUsuario(String email, String nombre, String password, Rol rol) {
        validarPassword(password);
        String emailNormalizado = Usuario.normalizarEmail(email);
        if (usuarioRepository.existsByEmail(emailNormalizado)) {
            throw new EmailYaRegistradoException(emailNormalizado);
        }
        Usuario usuario = new Usuario(emailNormalizado, passwordEncoder.encode(password), nombre, rol);
        return usuarioRepository.save(usuario);
    }

    private static void validarPassword(String password) {
        if (password == null || password.length() < PASSWORD_MIN_LENGTH) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos " + PASSWORD_MIN_LENGTH + " caracteres.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw new IllegalArgumentException(
                    "La contraseña no puede superar " + PASSWORD_MAX_BYTES + " bytes.");
        }
    }
}
