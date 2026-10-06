package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a usuarios. Los correos se guardan normalizados (ver {@link Usuario#normalizarEmail}),
 * por lo que las búsquedas exactas aprovechan el índice único de {@code email}.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);
}
