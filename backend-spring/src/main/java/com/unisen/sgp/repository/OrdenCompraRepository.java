package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.EstadoOrdenCompra;
import com.unisen.sgp.model.entity.OrdenCompra;
import com.unisen.sgp.model.entity.IncidenciaRecepcion;
import com.unisen.sgp.model.entity.ProgramacionEntrega;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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

/** Órdenes de compra de la empresa actual (@TenantId). */
public interface OrdenCompraRepository extends JpaRepository<OrdenCompra, Long>, JpaSpecificationExecutor<OrdenCompra> {

    /** Proveedor, autor y entrega vigente en la misma consulta; las líneas y sus productos, por lotes. */
    @Override
    @EntityGraph(attributePaths = {"proveedor", "creadoPor", "programacionEntrega", "programacionEntrega.registradaPor"})
    Page<OrdenCompra> findAll(Specification<OrdenCompra> spec, Pageable pageable);

    /** Calendario: todas las del período, sin paginar (el período está acotado). */
    @Override
    @EntityGraph(attributePaths = {"proveedor", "programacionEntrega"})
    List<OrdenCompra> findAll(Specification<OrdenCompra> spec);

    @Override
    @EntityGraph(attributePaths = {"proveedor", "creadoPor", "emitidaPor", "canceladaPor", "programacionEntrega"})
    Optional<OrdenCompra> findById(Long id);

    /**
     * Para cambiar su estado o registrar una recepción: bloquea la fila hasta el fin de la
     * transacción. Dos operaciones simultáneas sobre la misma orden se serializan y la segunda
     * ve el estado que dejó la primera.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OrdenCompra> findWithLockById(Long id);

    /** Último número de la empresa actual (0 si aún no hay órdenes). Llamar con la empresa bloqueada. */
    @Query("select coalesce(max(o.numero), 0) from OrdenCompra o")
    long ultimoNumero();

    List<OrdenCompra> findBySolicitudIdAndEstadoNotOrderByNumero(Long solicitudId, EstadoOrdenCompra estado);

    long countByProveedorIdAndEstadoIn(Long proveedorId, Collection<EstadoOrdenCompra> estados);

    /** Filtros combinables para el listado. */
    final class Filtros {

        private Filtros() {
        }

        /** Sin valor (null), no filtran. */
        public static Specification<OrdenCompra> conEstado(EstadoOrdenCompra estado) {
            return (root, query, cb) -> estado == null ? null : cb.equal(root.get("estado"), estado);
        }

        public static Specification<OrdenCompra> deProveedor(Long proveedorId) {
            return (root, query, cb) -> proveedorId == null ? null : cb.equal(root.get("proveedor").get("id"), proveedorId);
        }

        public static Specification<OrdenCompra> deSolicitud(Long solicitudId) {
            return (root, query, cb) -> solicitudId == null ? null : cb.equal(root.get("solicitudId"), solicitudId);
        }

        /** Emitidas en [inicio, fin). */
        public static Specification<OrdenCompra> emitidaEntre(Instant inicio, Instant fin) {
            return (root, query, cb) -> cb.and(cb.greaterThanOrEqualTo(root.get("emitidaEn"), inicio),
                    cb.lessThan(root.get("emitidaEn"), fin));
        }

        public static Specification<OrdenCompra> conNumero(Long numero) {
            return (root, query, cb) -> numero == null ? null : cb.equal(root.get("numero"), numero);
        }

        public static Specification<OrdenCompra> enEstados(Collection<EstadoOrdenCompra> estados) {
            return (root, query, cb) -> root.get("estado").in(estados);
        }

        /** Con programación vigente y sin ella. */
        public static Specification<OrdenCompra> programada(boolean programada) {
            return (root, query, cb) -> programada
                    ? cb.isNotNull(root.get("programacionEntrega"))
                    : cb.isNull(root.get("programacionEntrega"));
        }

        /** Fecha estimada vigente en [desde, hasta] (extremos opcionales); exige programación. */
        public static Specification<OrdenCompra> fechaEstimadaEntre(LocalDate desde, LocalDate hasta) {
            return (root, query, cb) -> {
                if (desde == null && hasta == null) {
                    return null;
                }
                Join<OrdenCompra, ProgramacionEntrega> p = root.join("programacionEntrega", JoinType.INNER);
                List<Predicate> condiciones = new ArrayList<>();
                if (desde != null) {
                    condiciones.add(cb.greaterThanOrEqualTo(p.get("fechaEstimada"), desde));
                }
                if (hasta != null) {
                    condiciones.add(cb.lessThanOrEqualTo(p.get("fechaEstimada"), hasta));
                }
                return cb.and(condiciones.toArray(Predicate[]::new));
            };
        }

        /** Fecha estimada vigente anterior a {@code dia} (retrasadas) o desde ese día (a tiempo). */
        public static Specification<OrdenCompra> fechaEstimadaAntesDe(LocalDate dia, boolean antes) {
            return (root, query, cb) -> {
                Join<OrdenCompra, ProgramacionEntrega> p = root.join("programacionEntrega", JoinType.INNER);
                return antes ? cb.lessThan(p.get("fechaEstimada"), dia) : cb.greaterThanOrEqualTo(p.get("fechaEstimada"), dia);
            };
        }

        public static Specification<OrdenCompra> fechaEstimadaEl(LocalDate dia) {
            return (root, query, cb) -> cb.equal(root.join("programacionEntrega", JoinType.INNER).get("fechaEstimada"), dia);
        }

        /** Alguna de sus recepciones informó incidencias. */
        public static Specification<OrdenCompra> conIncidencias() {
            return (root, query, cb) -> cb.exists(incidencias(root, query, cb));
        }

        /**
         * Necesita intervención: abierta y retrasada, sin programar o con incidencias.
         * ({@code hoy} en la zona del negocio.)
         */
        public static Specification<OrdenCompra> requiereIntervencion(LocalDate hoy) {
            return (root, query, cb) -> {
                Join<OrdenCompra, ProgramacionEntrega> p = root.join("programacionEntrega", JoinType.LEFT);
                return cb.and(root.get("estado").in(EstadoOrdenCompra.EMITIDA, EstadoOrdenCompra.RECIBIDA_PARCIALMENTE),
                        cb.or(cb.isNull(p.get("id")), cb.lessThan(p.get("fechaEstimada"), hoy),
                                cb.exists(incidencias(root, query, cb))));
            };
        }

        /** Fechas estimadas primero (las más próximas), después las órdenes sin programar; a igualdad, por número. */
        public static Specification<OrdenCompra> ordenadasPorFechaEstimada() {
            return (root, query, cb) -> {
                if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                    Join<OrdenCompra, ProgramacionEntrega> p = root.join("programacionEntrega", JoinType.LEFT);
                    query.orderBy(cb.asc(cb.selectCase().when(cb.isNull(p.get("id")), 1).otherwise(0)),
                            cb.asc(p.get("fechaEstimada")), cb.asc(root.get("numero")));
                }
                return null;
            };
        }

        private static Subquery<Long> incidencias(Root<OrdenCompra> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<IncidenciaRecepcion> incidencia = sub.from(IncidenciaRecepcion.class);
            sub.select(incidencia.get("id")).where(cb.equal(incidencia.get("recepcion").get("orden"), root));
            return sub;
        }
    }
}
