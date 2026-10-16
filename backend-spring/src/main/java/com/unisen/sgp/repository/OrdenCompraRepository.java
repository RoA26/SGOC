package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.EstadoOrdenCompra;
import com.unisen.sgp.model.entity.OrdenCompra;
import jakarta.persistence.LockModeType;
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

    /** Proveedor y autor en la misma consulta; las líneas y sus productos, por lotes. */
    @Override
    @EntityGraph(attributePaths = {"proveedor", "creadoPor"})
    Page<OrdenCompra> findAll(Specification<OrdenCompra> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"proveedor", "creadoPor", "emitidaPor", "canceladaPor"})
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
    }
}
