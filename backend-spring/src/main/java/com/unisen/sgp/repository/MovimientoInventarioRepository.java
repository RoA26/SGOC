package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.MovimientoInventario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Kárdex de la empresa actual (@TenantId). */
public interface MovimientoInventarioRepository
        extends JpaRepository<MovimientoInventario, Long>, JpaSpecificationExecutor<MovimientoInventario> {

    @Override
    @EntityGraph(attributePaths = {"producto", "usuario"})
    Page<MovimientoInventario> findAll(Specification<MovimientoInventario> spec, Pageable pageable);

    /** Idempotencia de los ajustes: la clave es única por empresa (uq_movimientos_inventario_clave). */
    @EntityGraph(attributePaths = {"producto", "usuario"})
    Optional<MovimientoInventario> findByClaveIdempotencia(String claveIdempotencia);

    /** Movimientos de las recepciones de una orden (historial): trazabilidad orden → recepción → kárdex. */
    @EntityGraph(attributePaths = {"producto", "detalleRecepcion", "detalleRecepcion.recepcion"})
    List<MovimientoInventario> findByDetalleRecepcionRecepcionOrdenIdOrderByIdAsc(Long ordenId);

    final class Filtros {

        private Filtros() {
        }

        public static Specification<MovimientoInventario> deProducto(Long productoId) {
            return (root, query, cb) -> productoId == null ? null : cb.equal(root.get("producto").get("id"), productoId);
        }
    }
}
