package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;

/** Empresas (tenants). Tabla global: no la filtra el tenant actual. */
public interface EmpresaRepository extends JpaRepository<Empresa, Long> {
}
