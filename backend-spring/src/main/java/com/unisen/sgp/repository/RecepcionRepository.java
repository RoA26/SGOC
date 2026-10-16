package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.Recepcion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Recepciones de la empresa actual (@TenantId). */
public interface RecepcionRepository extends JpaRepository<Recepcion, Long> {

    @Override
    @EntityGraph(attributePaths = {"orden", "orden.proveedor", "recibidaPor"})
    Page<Recepcion> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "recibidaPor")
    List<Recepcion> findByOrdenIdOrderByRecibidaEnAscIdAsc(Long ordenId);

    /** Idempotencia: la clave es única por empresa (uq_recepciones_clave). */
    @EntityGraph(attributePaths = {"orden", "recibidaPor"})
    Optional<Recepcion> findByClaveIdempotencia(String claveIdempotencia);
}
