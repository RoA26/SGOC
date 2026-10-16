package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.ProveedorReferencia;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Solo lectura: incluye los proveedores dados de baja (ver {@link ProveedorReferencia}). */
public interface ProveedorReferenciaRepository extends Repository<ProveedorReferencia, Long> {

    Optional<ProveedorReferencia> findById(Long id);
}
