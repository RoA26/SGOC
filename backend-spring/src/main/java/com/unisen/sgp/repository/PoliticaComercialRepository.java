package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.PoliticaComercial;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Política comercial de la empresa actual (@TenantId): a lo sumo una fila. */
public interface PoliticaComercialRepository extends JpaRepository<PoliticaComercial, Long> {

    Optional<PoliticaComercial> findFirstByOrderByIdAsc();
}
