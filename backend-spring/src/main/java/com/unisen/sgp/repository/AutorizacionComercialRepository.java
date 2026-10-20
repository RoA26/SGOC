package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.AutorizacionComercial;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Autorizaciones de excepción de la empresa actual (@TenantId). */
public interface AutorizacionComercialRepository extends JpaRepository<AutorizacionComercial, Long> {

    /** Bloquea la autorización: dos ventas simultáneas no pueden usar el mismo código. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AutorizacionComercial> findWithLockByCodigo(String codigo);

    /** Sin bloqueo: para la cotización (solo lectura). */
    Optional<AutorizacionComercial> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    @Override
    @EntityGraph(attributePaths = {"creadaPor", "usadaPor"})
    Page<AutorizacionComercial> findAll(Pageable pageable);
}
