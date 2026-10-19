package com.unisen.sgp.service;

import static com.unisen.sgp.repository.OrdenCompraRepository.Filtros.emitidaEntre;
import static com.unisen.sgp.repository.OrdenCompraRepository.Filtros.enEstados;
import static com.unisen.sgp.repository.OrdenCompraRepository.Filtros.fechaEstimadaEntre;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.model.dto.CalendarioDTO;
import com.unisen.sgp.model.dto.EventoCalendarioDTO;
import com.unisen.sgp.model.dto.EventoCalendarioDTO.Tipo;
import com.unisen.sgp.model.entity.EstadoOrdenCompra;
import com.unisen.sgp.model.entity.EstadoSolicitud;
import com.unisen.sgp.model.entity.OrdenCompra;
import com.unisen.sgp.model.entity.ProgramacionEntrega;
import com.unisen.sgp.model.entity.Recepcion;
import com.unisen.sgp.model.entity.SituacionEntrega;
import com.unisen.sgp.model.entity.Solicitud;
import com.unisen.sgp.repository.OrdenCompraRepository;
import com.unisen.sgp.repository.RecepcionRepository;
import com.unisen.sgp.repository.SolicitudRepository;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.security.UsuarioActual;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calendario operativo de la empresa: eventos derivados de órdenes, programaciones,
 * recepciones y solicitudes (no hay tabla de calendario que pueda desincronizarse). Siempre
 * dentro de una empresa: el SUPER_ADMIN debe estar supervisando una (en modo global, 400).
 *
 * <p>Según el rol: el gestor ve todos los tipos; el trabajador, las entregas, las recepciones y
 * sus propias solicitudes pendientes (nunca costos: los eventos no los llevan).
 */
@Service
@Transactional(readOnly = true)
public class CalendarioOperativoService {

    /** Seis semanas (la cuadrícula mensual completa) con margen. */
    public static final int DIAS_MAXIMOS = 62;
    /** Límite defensivo de eventos por consulta. */
    static final int EVENTOS_MAXIMOS = 2_000;

    private static final Set<EstadoOrdenCompra> EMITIDAS =
            EnumSet.of(EstadoOrdenCompra.EMITIDA, EstadoOrdenCompra.RECIBIDA_PARCIALMENTE, EstadoOrdenCompra.RECIBIDA);

    private final OrdenCompraRepository ordenRepository;
    private final RecepcionRepository recepcionRepository;
    private final SolicitudRepository solicitudRepository;
    private final CalendarioNegocio calendario;

    public CalendarioOperativoService(OrdenCompraRepository ordenRepository, RecepcionRepository recepcionRepository,
                                      SolicitudRepository solicitudRepository, CalendarioNegocio calendario) {
        this.ordenRepository = ordenRepository;
        this.recepcionRepository = recepcionRepository;
        this.solicitudRepository = solicitudRepository;
        this.calendario = calendario;
    }

    /**
     * @param tipos tipos pedidos; vacío o null = todos los que permite el rol
     * @throws CampoInvalidoException si faltan las fechas, son incoherentes o el período supera {@value #DIAS_MAXIMOS} días
     */
    @PreAuthorize(Permisos.LECTURA_OPERATIVA)
    public CalendarioDTO eventos(LocalDate desde, LocalDate hasta, Set<Tipo> tipos) {
        TenantContextHolder.requerirEmpresa();
        if (desde == null || hasta == null) {
            throw new CampoInvalidoException(desde == null ? "desde" : "hasta", "Indica el período del calendario.");
        }
        CalendarioNegocio.Intervalo intervalo = calendario.intervalo(desde, hasta, DIAS_MAXIMOS);
        var usuario = UsuarioActual.obtener();
        boolean gestor = usuario.getRol().esGestor();
        Set<Tipo> pedidos = tipos == null || tipos.isEmpty() ? EnumSet.allOf(Tipo.class) : EnumSet.copyOf(tipos);
        if (!gestor) {
            pedidos.remove(Tipo.ORDEN_EMITIDA);
        }
        LocalDate hoy = calendario.hoy();
        List<EventoCalendarioDTO> eventos = new ArrayList<>();

        if (pedidos.contains(Tipo.ORDEN_EMITIDA)) {
            for (OrdenCompra orden : ordenRepository.findAll(emitidaEntre(intervalo.inicio(), intervalo.fin()))) {
                ZonedDateTime local = orden.getEmitidaEn().atZone(calendario.zona());
                eventos.add(new EventoCalendarioDTO("emision-" + orden.getId(), Tipo.ORDEN_EMITIDA,
                        orden.getEstado().name(), local.toLocalDate(), false, hora(local), null, orden.getEmitidaEn(),
                        "Emitida " + OrdenCompraService.numeroVisible(orden.getNumero()),
                        orden.getProveedor().getRazonSocial(), orden.getId(), orden.getNumero(),
                        orden.getProveedor().getRazonSocial(), orden.getSolicitudId(), null, false, false, 0));
            }
        }
        if (pedidos.contains(Tipo.ENTREGA)) {
            Specification<OrdenCompra> spec = enEstados(EMITIDAS).and(fechaEstimadaEntre(desde, hasta));
            for (OrdenCompra orden : ordenRepository.findAll(spec)) {
                eventos.add(entrega(orden, hoy));
            }
        }
        if (pedidos.contains(Tipo.RECEPCION)) {
            List<Recepcion> recepciones = recepcionRepository
                    .findByRecibidaEnGreaterThanEqualAndRecibidaEnLessThanOrderByRecibidaEnAscIdAsc(intervalo.inicio(),
                            intervalo.fin());
            // Para saber cuál completó cada orden aunque quede fuera del período mostrado.
            List<Long> recibidas = recepciones.stream().map(Recepcion::getOrden)
                    .filter(o -> o.getEstado() == EstadoOrdenCompra.RECIBIDA).map(OrdenCompra::getId).distinct().toList();
            Map<Long, Long> ultimaConUnidades = new HashMap<>();
            if (!recibidas.isEmpty()) {
                recepcionRepository.ultimaConUnidades(recibidas)
                        .forEach(u -> ultimaConUnidades.put(u.getOrdenId(), u.getRecepcionId()));
            }
            for (Recepcion recepcion : recepciones) {
                eventos.add(recepcion(recepcion, ultimaConUnidades));
            }
        }
        if (pedidos.contains(Tipo.SOLICITUD_PENDIENTE)) {
            Specification<Solicitud> spec = SolicitudRepository.Filtros.conEstado(EstadoSolicitud.PENDIENTE)
                    .and(SolicitudRepository.Filtros.creadaEntre(intervalo.inicio(), intervalo.fin()));
            if (!gestor) {
                spec = spec.and(SolicitudRepository.Filtros.deSolicitante(usuario.getId()));
            }
            for (Solicitud solicitud : solicitudRepository.findAll(spec)) {
                ZonedDateTime local = solicitud.getFecha().atZone(calendario.zona());
                eventos.add(new EventoCalendarioDTO("solicitud-" + solicitud.getId(), Tipo.SOLICITUD_PENDIENTE,
                        EstadoSolicitud.PENDIENTE.name(), local.toLocalDate(), false, hora(local), null,
                        solicitud.getFecha(), "Solicitud #" + solicitud.getId() + " por aprobar",
                        solicitud.getSolicitante().getNombre(), null, null, null, solicitud.getId(), null, false, false,
                        0));
            }
        }

        eventos.sort(Comparator.comparing(EventoCalendarioDTO::fecha)
                .thenComparing(e -> !e.todoElDia())
                .thenComparing(e -> e.horaInicio() == null ? LocalTime.MIDNIGHT : e.horaInicio())
                .thenComparing(EventoCalendarioDTO::id));
        boolean truncado = eventos.size() > EVENTOS_MAXIMOS;
        return new CalendarioDTO(desde, hasta, hoy, calendario.zona().getId(),
                truncado ? List.copyOf(eventos.subList(0, EVENTOS_MAXIMOS)) : eventos, truncado);
    }

    /**
     * Entrega en su fecha estimada vigente: RETRASADA (pasada con pendiente), CONFIRMADA (por el
     * proveedor), PROGRAMADA (estimación interna) o COMPLETA (ya se recibió todo).
     */
    private EventoCalendarioDTO entrega(OrdenCompra orden, LocalDate hoy) {
        ProgramacionEntrega p = orden.getProgramacionEntrega();
        SituacionEntrega situacion = SituacionEntrega.de(orden, hoy);
        String estado = switch (situacion) {
            case RETRASADA -> "RETRASADA";
            case COMPLETA -> "COMPLETA";
            default -> p.isConfirmadaProveedor() ? "CONFIRMADA" : "PROGRAMADA";
        };
        boolean parcial = orden.getEstado() == EstadoOrdenCompra.RECIBIDA_PARCIALMENTE;
        String detalle = orden.getProveedor().getRazonSocial()
                + (p.getReferenciaProveedor() == null ? "" : " · Ref. " + p.getReferenciaProveedor());
        return new EventoCalendarioDTO("entrega-" + orden.getId(), Tipo.ENTREGA, estado, p.getFechaEstimada(),
                !p.datos().tieneFranja(), p.getHoraDesde(), p.getHoraHasta(), null,
                "Entrega " + OrdenCompraService.numeroVisible(orden.getNumero()), detalle, orden.getId(),
                orden.getNumero(), orden.getProveedor().getRazonSocial(), orden.getSolicitudId(), null,
                p.isConfirmadaProveedor(), parcial, 0);
    }

    /** COMPLETA: la última con unidades de una orden ya recibida. SOLO_INCIDENCIAS: no se aceptó nada. */
    private EventoCalendarioDTO recepcion(Recepcion recepcion, Map<Long, Long> ultimaConUnidades) {
        OrdenCompra orden = recepcion.getOrden();
        String estado;
        if (recepcion.getDetalles().isEmpty()) {
            estado = "SOLO_INCIDENCIAS";
        } else if (orden.getEstado() == EstadoOrdenCompra.RECIBIDA
                && recepcion.getId().equals(ultimaConUnidades.get(orden.getId()))) {
            estado = "COMPLETA";
        } else {
            estado = "PARCIAL";
        }
        int unidades = recepcion.getDetalles().stream().mapToInt(d -> d.getCantidad()).sum();
        ZonedDateTime local = recepcion.getRecibidaEn().atZone(calendario.zona());
        return new EventoCalendarioDTO("recepcion-" + recepcion.getId(), Tipo.RECEPCION, estado, local.toLocalDate(),
                false, hora(local), null, recepcion.getRecibidaEn(),
                "Recepción " + OrdenCompraService.numeroVisible(orden.getNumero()),
                unidades + " unidades · " + recepcion.getRecibidaPor().getNombre(), orden.getId(), orden.getNumero(),
                orden.getProveedor().getRazonSocial(), orden.getSolicitudId(), recepcion.getId(), false,
                "PARCIAL".equals(estado), recepcion.getIncidencias().size());
    }

    private static LocalTime hora(ZonedDateTime local) {
        return local.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
    }
}
