package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.DocumentoOrdenCompra;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Versiones documentales de las órdenes de la empresa actual (@TenantId). */
public interface DocumentoOrdenCompraRepository extends JpaRepository<DocumentoOrdenCompra, Long> {

    /** La versión vigente: la de número más alto. */
    Optional<DocumentoOrdenCompra> findFirstByOrdenIdOrderByVersionDesc(Long ordenId);

    @EntityGraph(attributePaths = "generadoPor")
    List<DocumentoOrdenCompra> findByOrdenIdOrderByVersionAsc(Long ordenId);
}
