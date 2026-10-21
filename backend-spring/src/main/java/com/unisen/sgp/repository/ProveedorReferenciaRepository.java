package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.ProveedorReferencia;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Solo lectura: incluye los proveedores dados de baja (ver {@link ProveedorReferencia}). */
public interface ProveedorReferenciaRepository extends Repository<ProveedorReferencia, Long> {

    Optional<ProveedorReferencia> findById(Long id);

    /**
     * Proveedor de la empresa con ese NIT, también si está dado de baja (el NIT sigue ocupado).
     *
     * <p><b>Presupone un contexto de empresa</b> ({@code TenantContextHolder}) al abrir la
     * transacción: el NIT es único por empresa, no global. Sin empresa, Hibernate usa el tenant
     * raíz, que ve todas, y el mismo NIT de dos empresas daría un resultado ajeno o un error.
     * Hoy solo lo usa la seed de demostración, dentro de la empresa que siembra.
     */
    Optional<ProveedorReferencia> findByNit(String nit);
}
