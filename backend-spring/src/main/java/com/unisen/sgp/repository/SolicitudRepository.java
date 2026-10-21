package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.EstadoSolicitud;
import com.unisen.sgp.model.entity.Solicitud;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Override
    @EntityGraph(attributePaths = "solicitante")
    List<Solicitud> findAll(Specification<Solicitud> spec);

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

        /** Creadas en [inicio, fin). */
        public static Specification<Solicitud> creadaEntre(Instant inicio, Instant fin) {
            return (root, query, cb) -> cb.and(cb.greaterThanOrEqualTo(root.get("fecha"), inicio),
                    cb.lessThan(root.get("fecha"), fin));
        }

        public static Specification<Solicitud> deSolicitante(Long usuarioId) {
            return (root, query, cb) -> cb.equal(root.get("solicitante").get("id"), usuarioId);
        }
    }

    /**
     * Actividad de los usuarios indicados como solicitantes (vista de personal): la última de su
     * historia y cuántas desde {@code desde}. Solo la empresa actual (@TenantId).
     */
    @Query("""
            select s.solicitante.id as usuarioId, max(s.fecha) as ultima,
                   sum(case when s.fecha >= :desde then 1 else 0 end) as recientes
            from Solicitud s
            where s.solicitante.id in :usuarioIds
            group by s.solicitante.id""")
    List<ActividadPorUsuario> actividadPorUsuario(@Param("usuarioIds") Collection<Long> usuarioIds,
                                                  @Param("desde") Instant desde);
}
