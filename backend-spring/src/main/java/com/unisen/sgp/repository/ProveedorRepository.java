package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;

/** Todas las consultas excluyen los proveedores dados de baja ({@code @SQLRestriction}). */
public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {
}
