package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.CambioPrecio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Historial de condiciones de venta de la empresa actual (@TenantId). */
public interface CambioPrecioRepository extends JpaRepository<CambioPrecio, Long> {

    @EntityGraph(attributePaths = {"producto", "usuario"})
    Page<CambioPrecio> findByProductoId(Long productoId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"producto", "usuario"})
    Page<CambioPrecio> findAll(Pageable pageable);
}
