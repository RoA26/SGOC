package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.DetalleSolicitud;
import com.unisen.sgp.model.entity.EstadoSolicitud;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetalleSolicitudRepository extends JpaRepository<DetalleSolicitud, Long> {

    /** Solicitudes en los estados indicados que incluyen el producto (una línea por producto y solicitud). */
    long countByProductoIdAndSolicitudEstadoIn(Long productoId, Collection<EstadoSolicitud> estados);
}
