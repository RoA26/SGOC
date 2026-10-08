package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a usuarios. Username y correo se guardan normalizados en minúsculas
 * (ver {@link Usuario#normalizarUsername} y {@link Usuario#normalizarEmail}), así que las
 * búsquedas exactas aprovechan sus índices únicos.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Con su empresa: el login y cada petición necesitan saber si está activa. */
    @EntityGraph(attributePaths = "empresa")
    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
