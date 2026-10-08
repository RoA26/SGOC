package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.Empresa;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Empresas (tenants). Tabla global: no la filtra el tenant actual. */
public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    boolean existsByNit(String nit);

    boolean existsByCodigoEmpresa(String codigoEmpresa);

    /** Para el registro de trabajadores: el código se busca ya normalizado. */
    Optional<Empresa> findByCodigoEmpresa(String codigoEmpresa);
}
