package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.EstadoSolicitud;
import com.unisen.sgp.model.entity.Solicitud;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Solicitud con su detalle. Los campos de revisión se omiten mientras está PENDIENTE. */
public record SolicitudResponseDTO(
        Long id,
        Instant fecha,
        EstadoSolicitud estado,
        String justificacion,
        UsuarioResumenDTO solicitante,
        List<DetalleSolicitudResponseDTO> detalles,
        // Suma de las líneas con los precios actuales del catálogo.
        BigDecimal totalEstimado,
        UsuarioResumenDTO revisadoPor,
        Instant fechaRevision,
        String comentarioRevision) {

    /** Debe invocarse dentro de la transacción: recorre asociaciones LAZY. */
    public static SolicitudResponseDTO from(Solicitud solicitud) {
        List<DetalleSolicitudResponseDTO> detalles = solicitud.getDetalles().stream()
                .map(DetalleSolicitudResponseDTO::from)
                .toList();
        BigDecimal total = detalles.stream()
                .map(DetalleSolicitudResponseDTO::subtotalEstimado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SolicitudResponseDTO(
                solicitud.getId(),
                solicitud.getFecha(),
                solicitud.getEstado(),
                solicitud.getJustificacion(),
                UsuarioResumenDTO.from(solicitud.getSolicitante()),
                detalles,
                total,
                UsuarioResumenDTO.from(solicitud.getRevisadoPor()),
                solicitud.getFechaRevision(),
                solicitud.getComentarioRevision());
    }
}
