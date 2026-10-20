package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.DevolucionVenta;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Devoluciones y anulaciones de ventas de la empresa actual (@TenantId). */
public interface DevolucionVentaRepository extends JpaRepository<DevolucionVenta, Long> {

    Optional<DevolucionVenta> findByClaveIdempotencia(String clave);

    @EntityGraph(attributePaths = {"registradaPor", "detalles"})
    List<DevolucionVenta> findByVentaIdOrderByRegistradaEnAscIdAsc(Long ventaId);
}
