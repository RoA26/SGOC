package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.Existencia;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

/** Existencias de la empresa actual (@TenantId). */
public interface ExistenciaRepository extends JpaRepository<Existencia, Long>, JpaSpecificationExecutor<Existencia> {

    @Override
    @EntityGraph(attributePaths = "producto")
    Page<Existencia> findAll(Specification<Existencia> spec, Pageable pageable);

    Optional<Existencia> findByProductoId(Long productoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Existencia> findWithLockByProductoId(Long productoId);

    /**
     * Bloquea las filas de varios productos <strong>siempre en orden de producto</strong>: dos
     * recepciones con productos en común los bloquean en la misma secuencia y no pueden
     * esperarse mutuamente (interbloqueo).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Existencia> findWithLockByProductoIdInOrderByProductoIdAsc(Collection<Long> productoIds);

    /** Filtros del listado de inventario (solo productos activos). */
    final class Filtros {

        private Filtros() {
        }

        public static Specification<Existencia> deProductosActivos() {
            return (root, query, cb) -> cb.isTrue(root.get("producto").get("activo"));
        }

        /** Coincidencia parcial, sin distinguir mayúsculas, en nombre, SKU o código de barras. */
        public static Specification<Existencia> conTexto(String texto) {
            return (root, query, cb) -> {
                if (texto == null) {
                    return null;
                }
                String patron = "%" + texto.toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                var producto = root.get("producto");
                return cb.or(
                        cb.like(cb.lower(producto.get("nombre")), patron, '\\'),
                        cb.like(cb.lower(producto.get("sku")), patron, '\\'),
                        cb.like(cb.lower(producto.get("codigoBarras")), patron, '\\'));
            };
        }

        public static Specification<Existencia> agotadas() {
            return (root, query, cb) -> cb.equal(root.get("cantidad"), 0);
        }

        /** Con unidades, pero en el mínimo o por debajo. */
        public static Specification<Existencia> criticas() {
            return (root, query, cb) -> cb.and(
                    cb.greaterThan(root.get("cantidad"), 0),
                    cb.lessThanOrEqualTo(root.get("cantidad"), root.get("producto").<Integer>get("stockMinimo")));
        }

        public static Specification<Existencia> normales() {
            return (root, query, cb) -> cb.and(
                    cb.greaterThan(root.get("cantidad"), 0),
                    cb.greaterThan(root.get("cantidad"), root.get("producto").<Integer>get("stockMinimo")));
        }
    }
}
