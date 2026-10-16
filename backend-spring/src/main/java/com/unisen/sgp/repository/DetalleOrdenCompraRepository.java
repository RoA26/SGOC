package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.DetalleOrdenCompra;
import com.unisen.sgp.model.entity.EstadoOrdenCompra;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetalleOrdenCompraRepository extends JpaRepository<DetalleOrdenCompra, Long> {

    /** Órdenes en los estados indicados que incluyen el producto (una línea por producto y orden). */
    long countByProductoIdAndOrdenEstadoIn(Long productoId, Collection<EstadoOrdenCompra> estados);
}
