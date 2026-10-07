package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a usuarios. Username y correo se guardan normalizados en minúsculas
 * (ver {@link Usuario#normalizarUsername} y {@link Usuario#normalizarEmail}), así que las
 * búsquedas exactas aprovechan sus índices únicos.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
