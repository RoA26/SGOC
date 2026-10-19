package com.unisen.sgp.service;

import com.unisen.sgp.exception.RecursoNoEncontradoException;
import com.unisen.sgp.model.dto.DocumentoOrdenDTO;
import com.unisen.sgp.model.dto.EventoHistorialDTO;
import com.unisen.sgp.model.dto.EventoHistorialDTO.Tipo;
import com.unisen.sgp.model.dto.OrdenCompraResponseDTO;
import com.unisen.sgp.model.dto.ProgramacionEntregaDTO;
import com.unisen.sgp.model.dto.RecepcionResponseDTO;
import com.unisen.sgp.model.dto.UsuarioResumenDTO;
import com.unisen.sgp.model.entity.DocumentoOrdenCompra;
import com.unisen.sgp.model.entity.EstadoOrdenCompra;
import com.unisen.sgp.model.entity.MovimientoInventario;
import com.unisen.sgp.model.entity.OrdenCompra;
import com.unisen.sgp.model.entity.ProgramacionEntrega;
import com.unisen.sgp.model.entity.Recepcion;
import com.unisen.sgp.repository.DocumentoOrdenCompraRepository;
import com.unisen.sgp.repository.MovimientoInventarioRepository;
import com.unisen.sgp.repository.OrdenCompraRepository;
import com.unisen.sgp.repository.ProgramacionEntregaRepository;
import com.unisen.sgp.repository.RecepcionRepository;
import com.unisen.sgp.repository.SolicitudRepository;
import com.unisen.sgp.security.Permisos;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Secuencia completa de una orden, derivada de lo que ya se guarda: no duplica datos en una
 * tabla de eventos. Un número fijo de consultas por orden.
 */
@Service
@Transactional(readOnly = true)
public class HistorialOrdenService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final OrdenCompraRepository ordenRepository;
    private final SolicitudRepository solicitudRepository;
    private final DocumentoOrdenCompraRepository documentoRepository;
    private final ProgramacionEntregaRepository programacionRepository;
    private final RecepcionRepository recepcionRepository;
    private final MovimientoInventarioRepository movimientoRepository;

    public HistorialOrdenService(OrdenCompraRepository ordenRepository, SolicitudRepository solicitudRepository,
                                 DocumentoOrdenCompraRepository documentoRepository,
                                 ProgramacionEntregaRepository programacionRepository,
                                 RecepcionRepository recepcionRepository,
                                 MovimientoInventarioRepository movimientoRepository) {
        this.ordenRepository = ordenRepository;
        this.solicitudRepository = solicitudRepository;
        this.documentoRepository = documentoRepository;
        this.programacionRepository = programacionRepository;
        this.recepcionRepository = recepcionRepository;
        this.movimientoRepository = movimientoRepository;
    }

    @PreAuthorize(Permisos.GESTION)
    public List<EventoHistorialDTO> deOrden(Long ordenId) {
        OrdenCompra orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> RecursoNoEncontradoException.ordenCompra(ordenId));
        List<EventoHistorialDTO> eventos = new ArrayList<>();

        if (orden.getSolicitudId() != null) {
            solicitudRepository.findById(orden.getSolicitudId()).ifPresent(solicitud -> {
                eventos.add(EventoHistorialDTO.simple(Tipo.SOLICITUD_CREADA, solicitud.getFecha(),
                        UsuarioResumenDTO.from(solicitud.getSolicitante()), "Solicitud #" + solicitud.getId() + ": "
                                + solicitud.getJustificacion()));
                if (solicitud.getFechaRevision() != null) {
                    eventos.add(EventoHistorialDTO.simple(Tipo.SOLICITUD_APROBADA, solicitud.getFechaRevision(),
                            UsuarioResumenDTO.from(solicitud.getRevisadoPor()),
                            solicitud.getComentarioRevision() == null ? "Solicitud aprobada."
                                    : "Solicitud aprobada: " + solicitud.getComentarioRevision()));
                }
            });
        }
        eventos.add(EventoHistorialDTO.simple(Tipo.ORDEN_CREADA, orden.getCreadoEn(),
                UsuarioResumenDTO.from(orden.getCreadoPor()), "Orden creada en borrador."));
        if (orden.getEmitidaEn() != null) {
            eventos.add(EventoHistorialDTO.simple(Tipo.ORDEN_EMITIDA, orden.getEmitidaEn(),
                    UsuarioResumenDTO.from(orden.getEmitidaPor()),
                    frase("Orden emitida al proveedor " + orden.getProveedor().getRazonSocial())));
        }
        for (DocumentoOrdenCompra documento : documentoRepository.findByOrdenIdOrderByVersionAsc(ordenId)) {
            eventos.add(new EventoHistorialDTO(Tipo.DOCUMENTO_GENERADO, documento.getGeneradoEn(),
                    UsuarioResumenDTO.from(documento.getGeneradoPor()), "Documento Word, versión " + documento.getVersion()
                    + (DocumentoOrdenCompra.MOTIVO_EMISION.equals(documento.getMotivo()) ? ", generado al emitir la orden." : "."),
                    DocumentoOrdenDTO.from(documento), null, null, null));
        }
        for (ProgramacionEntrega programacion : programacionRepository.findByOrdenIdOrderByVersionAsc(ordenId)) {
            boolean primera = programacion.getVersion() == 1;
            String descripcion = (primera ? "Entrega programada para el " : "Entrega reprogramada para el ")
                    + DIA.format(programacion.getFechaEstimada())
                    + (programacion.isConfirmadaProveedor() ? " (confirmada por el proveedor)" : " (estimada)")
                    + (programacion.getMotivoCambio() == null ? "." : ". Motivo: " + programacion.getMotivoCambio());
            eventos.add(new EventoHistorialDTO(primera ? Tipo.ENTREGA_PROGRAMADA : Tipo.ENTREGA_REPROGRAMADA,
                    programacion.getRegistradaEn(), UsuarioResumenDTO.from(programacion.getRegistradaPor()), descripcion,
                    null, ProgramacionEntregaDTO.from(programacion), null, null));
        }

        Map<Long, List<EventoHistorialDTO.Movimiento>> movimientos = movimientoRepository
                .findByDetalleRecepcionRecepcionOrdenIdOrderByIdAsc(ordenId).stream()
                .collect(Collectors.groupingBy(m -> m.getDetalleRecepcion().getRecepcion().getId(),
                        Collectors.mapping(HistorialOrdenService::movimiento, Collectors.toList())));
        List<Recepcion> recepciones = recepcionRepository.findByOrdenIdOrderByRecibidaEnAscIdAsc(ordenId);
        Recepcion finalConUnidades = orden.getEstado() == EstadoOrdenCompra.RECIBIDA
                ? recepciones.stream().filter(r -> !r.getDetalles().isEmpty()).reduce((a, b) -> b).orElse(null)
                : null;
        for (Recepcion recepcion : recepciones) {
            RecepcionResponseDTO dto = RecepcionResponseDTO.from(recepcion);
            Tipo tipo = recepcion.getDetalles().isEmpty() ? Tipo.RECEPCION_CON_INCIDENCIAS
                    : recepcion.equals(finalConUnidades) ? Tipo.RECEPCION_FINAL : Tipo.RECEPCION_PARCIAL;
            String descripcion = dto.unidades() + " unidades aceptadas"
                    + (dto.incidencias().isEmpty() ? "" : ", " + dto.incidencias().size() + " incidencias") + ".";
            eventos.add(new EventoHistorialDTO(tipo, recepcion.getRecibidaEn(), dto.recibidaPor(), descripcion, null, null,
                    dto, movimientos.getOrDefault(recepcion.getId(), List.of())));
        }
        if (orden.getCanceladaEn() != null) {
            eventos.add(EventoHistorialDTO.simple(Tipo.ORDEN_CANCELADA, orden.getCanceladaEn(),
                    UsuarioResumenDTO.from(orden.getCanceladaPor()), frase("Cancelada: " + orden.getMotivoCancelacion())));
        }
        // Orden estable: por instante y, a igualdad (emisión y documento comparten instante), por tipo.
        eventos.sort(Comparator.comparing(EventoHistorialDTO::instante).thenComparing(EventoHistorialDTO::tipo));
        return eventos;
    }

    /** Cierra con punto sin duplicarlo ("Aceros S.A.S." no termina en "S.A.S.."). */
    private static String frase(String texto) {
        return texto.endsWith(".") ? texto : texto + ".";
    }

    private static EventoHistorialDTO.Movimiento movimiento(MovimientoInventario m) {
        return new EventoHistorialDTO.Movimiento(m.getId(), m.getDetalleRecepcion().getId(),
                OrdenCompraResponseDTO.Producto.from(m.getProducto()), m.getCantidad(), m.getSaldoResultante());
    }
}
