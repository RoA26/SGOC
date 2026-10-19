package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.Recepcion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.unisen.sgp.model.entity.IncidenciaRecepcion;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.Collection;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Recepciones de la empresa actual (@TenantId). */
public interface RecepcionRepository extends JpaRepository<Recepcion, Long>, JpaSpecificationExecutor<Recepcion> {

    @Override
    @EntityGraph(attributePaths = {"orden", "orden.proveedor", "recibidaPor"})
    Page<Recepcion> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"orden", "orden.proveedor", "recibidaPor"})
    Page<Recepcion> findAll(Specification<Recepcion> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"orden", "orden.proveedor", "recibidaPor"})
    Optional<Recepcion> findById(Long id);

    /** Recepciones del período (calendario), con su orden y proveedor. */
    @EntityGraph(attributePaths = {"orden", "orden.proveedor", "recibidaPor"})
    List<Recepcion> findByRecibidaEnGreaterThanEqualAndRecibidaEnLessThanOrderByRecibidaEnAscIdAsc(Instant inicio,
                                                                                                  Instant fin);

    /** Resumen de recepciones por orden (listado de entregas): una consulta para toda la página. */
    interface ResumenPorOrden {
        Long getOrdenId();

        long getRecepciones();

        Instant getUltima();

        long getIncidencias();
    }

    @Query("""
            select r.orden.id as ordenId, count(distinct r.id) as recepciones, max(r.recibidaEn) as ultima,
                   count(i.id) as incidencias
            from Recepcion r left join r.incidencias i
            where r.orden.id in :ordenIds
            group by r.orden.id""")
    List<ResumenPorOrden> resumenPorOrden(@Param("ordenIds") Collection<Long> ordenIds);

    /**
     * Id de la última recepción con unidades aceptadas de cada orden (las recepciones de una orden
     * se registran con la orden bloqueada, así que el id más alto es la más reciente).
     */
    interface UltimaConUnidades {
        Long getOrdenId();

        Long getRecepcionId();
    }

    @Query("""
            select r.orden.id as ordenId, max(r.id) as recepcionId
            from Recepcion r
            where r.orden.id in :ordenIds and exists (select 1 from DetalleRecepcion d where d.recepcion = r)
            group by r.orden.id""")
    List<UltimaConUnidades> ultimaConUnidades(@Param("ordenIds") Collection<Long> ordenIds);

    /** Filtros combinables del listado; sin valor (null), no filtran. */
    final class Filtros {

        private Filtros() {
        }

        public static Specification<Recepcion> desde(Instant inicio) {
            return (root, query, cb) -> inicio == null ? null : cb.greaterThanOrEqualTo(root.get("recibidaEn"), inicio);
        }

        public static Specification<Recepcion> antesDe(Instant fin) {
            return (root, query, cb) -> fin == null ? null : cb.lessThan(root.get("recibidaEn"), fin);
        }

        public static Specification<Recepcion> deOrden(Long ordenId) {
            return (root, query, cb) -> ordenId == null ? null : cb.equal(root.get("orden").get("id"), ordenId);
        }

        public static Specification<Recepcion> deProveedor(Long proveedorId) {
            return (root, query, cb) -> proveedorId == null ? null
                    : cb.equal(root.get("orden").get("proveedor").get("id"), proveedorId);
        }

        public static Specification<Recepcion> registradaPor(Long usuarioId) {
            return (root, query, cb) -> usuarioId == null ? null : cb.equal(root.get("recibidaPor").get("id"), usuarioId);
        }

        /** true: solo con incidencias; false: solo sin ellas; null: todas. */
        public static Specification<Recepcion> conIncidencias(Boolean con) {
            return (root, query, cb) -> {
                if (con == null) {
                    return null;
                }
                Subquery<Long> sub = query.subquery(Long.class);
                var incidencia = sub.from(IncidenciaRecepcion.class);
                sub.select(incidencia.get("id")).where(cb.equal(incidencia.get("recepcion"), root));
                return con ? cb.exists(sub) : cb.not(cb.exists(sub));
            };
        }
    }

    @EntityGraph(attributePaths = "recibidaPor")
    List<Recepcion> findByOrdenIdOrderByRecibidaEnAscIdAsc(Long ordenId);

    /** Idempotencia: la clave es única por empresa (uq_recepciones_clave). */
    @EntityGraph(attributePaths = {"orden", "recibidaPor"})
    Optional<Recepcion> findByClaveIdempotencia(String claveIdempotencia);
}
