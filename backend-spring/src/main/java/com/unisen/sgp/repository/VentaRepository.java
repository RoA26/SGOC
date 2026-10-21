package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.EstadoVenta;
import com.unisen.sgp.model.entity.Venta;
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

/** Ventas de la empresa actual (@TenantId). */
public interface VentaRepository extends JpaRepository<Venta, Long>, JpaSpecificationExecutor<Venta> {

    /** Último número de la empresa actual (0 sin ventas). Llamar con la empresa bloqueada. */
    @Query("select coalesce(max(v.numero), 0) from Venta v")
    long ultimoNumero();

    Optional<Venta> findByClaveIdempotencia(String clave);

    /** Bloquea la venta: devoluciones y anulaciones simultáneas se serializan. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Venta> findWithLockById(Long id);

    @Override
    @EntityGraph(attributePaths = "vendidaPor")
    Page<Venta> findAll(Specification<Venta> spec, Pageable pageable);

    /** Filtros del historial. */
    final class Filtros {

        private Filtros() {
        }

        public static Specification<Venta> desde(Instant desde) {
            return (root, query, cb) -> desde == null ? null : cb.greaterThanOrEqualTo(root.get("vendidaEn"), desde);
        }

        public static Specification<Venta> hasta(Instant hasta) {
            return (root, query, cb) -> hasta == null ? null : cb.lessThan(root.get("vendidaEn"), hasta);
        }

        public static Specification<Venta> conEstado(EstadoVenta estado) {
            return (root, query, cb) -> estado == null ? null : cb.equal(root.get("estado"), estado);
        }

        public static Specification<Venta> deVendedor(Long usuarioId) {
            return (root, query, cb) -> usuarioId == null ? null : cb.equal(root.get("vendidaPor").get("id"), usuarioId);
        }

        public static Specification<Venta> conNumero(Long numero) {
            return (root, query, cb) -> numero == null ? null : cb.equal(root.get("numero"), numero);
        }
    }

    /**
     * Actividad de los usuarios indicados como vendedores (vista de personal): la última de su
     * historia y cuántas desde {@code desde}. Solo la empresa actual (@TenantId).
     */
    @Query("""
            select v.vendidaPor.id as usuarioId, max(v.vendidaEn) as ultima,
                   sum(case when v.vendidaEn >= :desde then 1 else 0 end) as recientes
            from Venta v
            where v.vendidaPor.id in :usuarioIds
            group by v.vendidaPor.id""")
    List<ActividadPorUsuario> actividadPorUsuario(@Param("usuarioIds") Collection<Long> usuarioIds,
                                                  @Param("desde") Instant desde);
}
