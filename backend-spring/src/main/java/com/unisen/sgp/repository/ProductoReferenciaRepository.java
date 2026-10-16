package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.ProductoReferencia;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Solo lectura: incluye los productos dados de baja (ver {@link ProductoReferencia}). */
public interface ProductoReferenciaRepository extends Repository<ProductoReferencia, Long> {

    List<ProductoReferencia> findAllById(Iterable<Long> ids);

    Optional<ProductoReferencia> findById(Long id);

    /** Búsqueda por escáner: el código de barras es único por empresa (incluye los dados de baja). */
    Optional<ProductoReferencia> findByCodigoBarras(String codigoBarras);

    Optional<ProductoReferencia> findBySku(String sku);
}
