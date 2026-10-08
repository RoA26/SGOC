package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.EstadoUsuario;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

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

    // Usuario no lleva @TenantId (el login ocurre antes de conocer la empresa): toda consulta
    // de gestión filtra la empresa de forma explícita con el tenant de la petición.

    Page<Usuario> findByEmpresa_IdAndRol(Long empresaId, Rol rol, Pageable pageable);

    Page<Usuario> findByEmpresa_IdAndRolAndEstado(Long empresaId, Rol rol, EstadoUsuario estado, Pageable pageable);

    /** Bloquea la fila: dos gestores no pueden cambiar a la vez el estado de la misma cuenta. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Usuario> findWithLockByIdAndEmpresa_IdAndRol(Long id, Long empresaId, Rol rol);
}
