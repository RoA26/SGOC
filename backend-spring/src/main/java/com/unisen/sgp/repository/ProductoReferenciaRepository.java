package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.ProductoReferencia;
import java.util.List;
import org.springframework.data.repository.Repository;

/** Solo lectura: incluye los productos dados de baja (ver {@link ProductoReferencia}). */
public interface ProductoReferenciaRepository extends Repository<ProductoReferencia, Long> {

    List<ProductoReferencia> findAllById(Iterable<Long> ids);
}
