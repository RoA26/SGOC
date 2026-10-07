package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.EstadoSolicitud;
import com.unisen.sgp.model.entity.Solicitud;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long>, JpaSpecificationExecutor<Solicitud> {

    /**
     * Página filtrada. Solicitante y revisor llegan en la misma consulta; las líneas y sus
     * productos se cargan por lotes (default_batch_fetch_size), sin N+1 ni paginar en memoria.
     */
    @Override
    @EntityGraph(attributePaths = {"solicitante", "revisadoPor"})
    Page<Solicitud> findAll(Specification<Solicitud> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"solicitante", "revisadoPor"})
    Optional<Solicitud> findById(Long id);

    /**
     * Para revisarla: bloquea la fila ({@code SELECT … FOR UPDATE}) hasta el fin de la
     * transacción. Si dos gestores la revisan a la vez, el segundo ya la ve revisada.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Solicitud> findWithLockById(Long id);

    /** Filtros combinables para el listado. */
    final class Filtros {

        private Filtros() {
        }

        /** Sin estado (null), no filtra. */
        public static Specification<Solicitud> conEstado(EstadoSolicitud estado) {
            return (root, query, cb) -> estado == null ? null : cb.equal(root.get("estado"), estado);
        }

        public static Specification<Solicitud> deSolicitante(Long usuarioId) {
            return (root, query, cb) -> cb.equal(root.get("solicitante").get("id"), usuarioId);
        }
    }
}
