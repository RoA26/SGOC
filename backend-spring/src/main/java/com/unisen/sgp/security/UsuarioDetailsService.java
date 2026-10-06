package com.unisen.sgp.security;

import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Carga usuarios por correo para Spring Security (login y validación de cada JWT). */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioPrincipal loadUserByUsername(String username) {
        return usuarioRepository.findByEmail(Usuario.normalizarEmail(username))
                .map(UsuarioPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado."));
    }
}
