package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.Producto;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Todas las consultas excluyen los productos dados de baja ({@code @SQLRestriction}). */
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /** Carga el proveedor en la misma consulta para evitar N+1 al construir el listado. */
    @Override
    @EntityGraph(attributePaths = "proveedor")
    Page<Producto> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "proveedor")
    Optional<Producto> findById(Long id);

    /** Productos activos de un proveedor. */
    long countByProveedorId(Long proveedorId);
}
