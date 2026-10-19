package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.ProgramacionEntrega;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Historial de programaciones de entrega de la empresa actual (@TenantId). */
public interface ProgramacionEntregaRepository extends JpaRepository<ProgramacionEntrega, Long> {

    @EntityGraph(attributePaths = "registradaPor")
    List<ProgramacionEntrega> findByOrdenIdOrderByVersionAsc(Long ordenId);
}
