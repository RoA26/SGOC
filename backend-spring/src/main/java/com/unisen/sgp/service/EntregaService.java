package com.unisen.sgp.service;

import static com.unisen.sgp.repository.OrdenCompraRepository.Filtros.conIncidencias;
import static com.unisen.sgp.repository.OrdenCompraRepository.Filtros.conNumero;
import static com.unisen.sgp.repository.OrdenCompraRepository.Filtros.deProveedor;
import static com.unisen.sgp.repository.OrdenCompraRepository.Filtros.enEstados;
import static com.unisen.sgp.repository.OrdenCompraRepository.Filtros.fechaEstimadaAntesDe;
import static com.unisen.sgp.repository.OrdenCompraRepository.Filtros.fechaEstimadaEl;
import static com.unisen.sgp.repository.OrdenCompraRepository.Filtros.fechaEstimadaEntre;
import static com.unisen.sgp.repository.OrdenCompraRepository.Filtros.ordenadasPorFechaEstimada;
import static com.unisen.sgp.repository.OrdenCompraRepository.Filtros.programada;
import static com.unisen.sgp.repository.OrdenCompraRepository.Filtros.requiereIntervencion;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.exception.RecursoNoEncontradoException;
import com.unisen.sgp.model.dto.EntregaDTO;
import com.unisen.sgp.model.dto.EntregaDetalleDTO;
import com.unisen.sgp.model.dto.ProgramacionEntregaDTO;
import com.unisen.sgp.model.dto.ProgramacionEntregaRequestDTO;
import com.unisen.sgp.model.dto.RecepcionResponseDTO;
import com.unisen.sgp.model.dto.ResumenEntregasDTO;
import com.unisen.sgp.model.entity.EstadoOrdenCompra;
import com.unisen.sgp.model.entity.OrdenCompra;
import com.unisen.sgp.model.entity.ProgramacionEntrega;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.OrdenCompraRepository;
import com.unisen.sgp.repository.ProgramacionEntregaRepository;
import com.unisen.sgp.repository.RecepcionRepository;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.security.UsuarioActual;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entregas de las órdenes emitidas: el gerente (ADMIN_EMPRESA) programa y supervisa; el
 * trabajador consulta lo pendiente y registra lo recibido ({@link RecepcionService}).
 *
 * <p>Fechas: la fecha estimada es un día local (sin hora) de la zona del negocio; "hoy" también.
 * Una entrega está RETRASADA si su fecha estimada vigente es anterior a hoy y quedan unidades.
 * Nada de esto se guarda: se deriva de la orden, su programación vigente y sus recepciones.
 */
@Service
@Transactional(readOnly = true)
public class EntregaService {

    private static final Logger log = LoggerFactory.getLogger(EntregaService.class);
    /** Una fecha estimada a más de dos años no es una planificación. */
    private static final int ANOS_MAXIMOS = 2;

    /** Abiertas: emitidas y pendientes de recibir (lo que ve el trabajador). */
    private static final Set<EstadoOrdenCompra> ABIERTAS =
            EnumSet.of(EstadoOrdenCompra.EMITIDA, EstadoOrdenCompra.RECIBIDA_PARCIALMENTE);
    /** Las que llegaron a enviarse al proveedor y no se cancelaron. */
    private static final Set<EstadoOrdenCompra> EMITIDAS =
            EnumSet.of(EstadoOrdenCompra.EMITIDA, EstadoOrdenCompra.RECIBIDA_PARCIALMENTE, EstadoOrdenCompra.RECIBIDA);

    /** Filtros del listado de entregas. */
    public enum Vista {
        /** Emitidas con unidades pendientes (por defecto). */
        PENDIENTES,
        SIN_PROGRAMAR,
        /** Con fecha estimada de hoy en adelante. */
        PROGRAMADAS,
        RETRASADAS,
        PARCIALES,
        COMPLETAS,
        CON_INCIDENCIAS,
        INTERVENCION,
        /** Pendientes, parciales y completas. */
        TODAS;

        /** Lo que puede consultar el trabajador: lo que queda por recibir. */
        boolean operativa() {
            return this == PENDIENTES || this == SIN_PROGRAMAR || this == PROGRAMADAS || this == RETRASADAS
                    || this == PARCIALES;
        }
    }

    public record Filtro(Vista vista, LocalDate desde, LocalDate hasta, Long proveedorId, Long numero) {
    }

    private final OrdenCompraRepository ordenRepository;
    private final ProgramacionEntregaRepository programacionRepository;
    private final RecepcionRepository recepcionRepository;
    private final UsuarioRepository usuarioRepository;
    private final CalendarioNegocio calendario;
    private final Clock clock;

    public EntregaService(OrdenCompraRepository ordenRepository, ProgramacionEntregaRepository programacionRepository,
                          RecepcionRepository recepcionRepository, UsuarioRepository usuarioRepository,
                          CalendarioNegocio calendario, Clock clock) {
        this.ordenRepository = ordenRepository;
        this.programacionRepository = programacionRepository;
        this.recepcionRepository = recepcionRepository;
        this.usuarioRepository = usuarioRepository;
        this.calendario = calendario;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ programación

    /**
     * Programa o reprograma la entrega (ADMIN_EMPRESA). Cada cambio es una versión nueva del
     * historial; la orden queda apuntando a la vigente. Repetir la programación vigente no crea
     * una versión. La orden se bloquea: dos cambios simultáneos se serializan y no comparten
     * número de versión (uq_programaciones_entrega_version es la red de seguridad).
     */
    @PreAuthorize(Permisos.ADMIN_EMPRESA)
    @Transactional
    public ProgramacionEntregaDTO programar(Long ordenId, ProgramacionEntregaRequestDTO dto) {
        TenantContextHolder.requerirEmpresa();
        OrdenCompra orden = ordenRepository.findWithLockById(ordenId)
                .orElseThrow(() -> RecursoNoEncontradoException.ordenCompra(ordenId));
        LocalDate hoy = calendario.hoy();
        ProgramacionEntrega vigente = orden.getProgramacionEntrega();
        // Una fecha pasada dejaría la entrega retrasada desde el registro; sí se conserva la vigente
        // (p. ej. para confirmarla con el proveedor sin moverla).
        boolean mismaFecha = vigente != null && vigente.getFechaEstimada().equals(dto.fechaEstimada());
        if (dto.fechaEstimada().isBefore(hoy) && !mismaFecha) {
            throw new CampoInvalidoException("fechaEstimada", "La fecha estimada no puede ser anterior a hoy.");
        }
        if (dto.fechaEstimada().isAfter(hoy.plusYears(ANOS_MAXIMOS))) {
            throw new CampoInvalidoException("fechaEstimada",
                    "La fecha estimada no puede superar " + ANOS_MAXIMOS + " años desde hoy.");
        }
        ProgramacionEntrega.Datos datos = datos(dto);
        Usuario usuario = usuarioRepository.getReferenceById(UsuarioActual.obtener().getId());
        ProgramacionEntrega resultado = orden.programarEntrega(datos, dto.motivo(), usuario,
                clock.instant().truncatedTo(ChronoUnit.MICROS));
        ordenRepository.saveAndFlush(orden);
        if (resultado != vigente) {
            log.info("Entrega de la orden {} programada (v{}) para el {} por {}", orden.getNumero(),
                    resultado.getVersion(), resultado.getFechaEstimada(), UsuarioActual.paraAuditoria());
        }
        return ProgramacionEntregaDTO.from(resultado);
    }

    private static ProgramacionEntrega.Datos datos(ProgramacionEntregaRequestDTO dto) {
        if ((dto.horaDesde() == null) != (dto.horaHasta() == null)) {
            throw new CampoInvalidoException(dto.horaDesde() == null ? "horaDesde" : "horaHasta",
                    "La franja necesita hora inicial y final (o ninguna: todo el día).");
        }
        if (dto.horaDesde() != null && !dto.horaDesde().isBefore(dto.horaHasta())) {
            throw new CampoInvalidoException("horaHasta", "La hora final debe ser posterior a la inicial.");
        }
        return new ProgramacionEntrega.Datos(dto.fechaEstimada(), dto.horaDesde(), dto.horaHasta(),
                Boolean.TRUE.equals(dto.confirmadaProveedor()), dto.referenciaProveedor(), dto.observaciones());
    }

    /** Todas las versiones de la programación, de la primera a la vigente. */
    @PreAuthorize(Permisos.LECTURA_OPERATIVA)
    public List<ProgramacionEntregaDTO> historial(Long ordenId) {
        buscarVisible(ordenId);
        return programacionRepository.findByOrdenIdOrderByVersionAsc(ordenId).stream()
                .map(ProgramacionEntregaDTO::from).toList();
    }

    // ------------------------------------------------------------------ consulta

    /**
     * Entregas de la empresa, de la fecha estimada más próxima a la más lejana (las órdenes sin
     * programar, al final). El trabajador solo consulta lo pendiente de recibir.
     */
    @PreAuthorize(Permisos.LECTURA_OPERATIVA)
    public Page<EntregaDTO> listar(Filtro filtro, Pageable pageable) {
        TenantContextHolder.requerirEmpresa();
        Vista vista = filtro.vista() == null ? Vista.PENDIENTES : filtro.vista();
        if (!esGestor() && !vista.operativa()) {
            throw new AccessDeniedException("El trabajador consulta las entregas pendientes.");
        }
        calendario.intervalo(filtro.desde(), filtro.hasta(), CalendarioNegocio.DIAS_MAXIMOS);
        LocalDate hoy = calendario.hoy();
        Specification<OrdenCompra> spec = segunVista(vista, hoy)
                .and(fechaEstimadaEntre(filtro.desde(), filtro.hasta()))
                .and(deProveedor(filtro.proveedorId()))
                .and(conNumero(filtro.numero()))
                .and(ordenadasPorFechaEstimada());
        Pageable sinOrden = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<OrdenCompra> pagina = ordenRepository.findAll(spec, sinOrden);
        Map<Long, RecepcionRepository.ResumenPorOrden> resumen = pagina.isEmpty() ? Map.of()
                : recepcionRepository.resumenPorOrden(pagina.map(OrdenCompra::getId).toList()).stream()
                .collect(Collectors.toMap(RecepcionRepository.ResumenPorOrden::getOrdenId, Function.identity()));
        return pagina.map(orden -> aDTO(orden, hoy, resumen.get(orden.getId())));
    }

    /** Cifras para la supervisión: qué hay pendiente y qué necesita intervención. */
    @PreAuthorize(Permisos.GESTION)
    public ResumenEntregasDTO resumen() {
        TenantContextHolder.requerirEmpresa();
        LocalDate hoy = calendario.hoy();
        Specification<OrdenCompra> abiertas = enEstados(ABIERTAS);
        return new ResumenEntregasDTO(
                ordenRepository.count(abiertas),
                ordenRepository.count(abiertas.and(fechaEstimadaEl(hoy))),
                ordenRepository.count(abiertas.and(fechaEstimadaAntesDe(hoy, true))),
                ordenRepository.count(abiertas.and(programada(false))),
                ordenRepository.count(enEstados(EnumSet.of(EstadoOrdenCompra.RECIBIDA_PARCIALMENTE))),
                ordenRepository.count(abiertas.and(conIncidencias())),
                ordenRepository.count(requiereIntervencion(hoy)));
    }

    /**
     * Lo que necesita quien recibe: líneas sin costos, recepciones anteriores e historial de la
     * programación. El trabajador no ve borradores ni canceladas (404, como si no existieran).
     */
    @PreAuthorize(Permisos.LECTURA_OPERATIVA)
    public EntregaDetalleDTO detalle(Long ordenId) {
        TenantContextHolder.requerirEmpresa();
        OrdenCompra orden = buscarVisible(ordenId);
        boolean verCostos = esGestor();
        LocalDate hoy = calendario.hoy();
        List<RecepcionResponseDTO> recepciones = recepcionRepository.findByOrdenIdOrderByRecibidaEnAscIdAsc(ordenId)
                .stream().map(r -> RecepcionResponseDTO.from(r).para(verCostos)).toList();
        Instant ultima = recepciones.isEmpty() ? null : recepciones.getLast().recibidaEn();
        long incidencias = recepciones.stream().mapToLong(r -> r.incidencias().size()).sum();
        return new EntregaDetalleDTO(
                EntregaDTO.from(orden, hoy, recepciones.size(), ultima, incidencias),
                orden.getObservaciones(),
                orden.getDetalles().stream().map(EntregaDetalleDTO.Linea::from).toList(),
                recepciones,
                programacionRepository.findByOrdenIdOrderByVersionAsc(ordenId).stream()
                        .map(ProgramacionEntregaDTO::from).toList());
    }

    private OrdenCompra buscarVisible(Long ordenId) {
        OrdenCompra orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> RecursoNoEncontradoException.ordenCompra(ordenId));
        if (!esGestor() && !EMITIDAS.contains(orden.getEstado())) {
            throw RecursoNoEncontradoException.ordenCompra(ordenId);
        }
        return orden;
    }

    private static Specification<OrdenCompra> segunVista(Vista vista, LocalDate hoy) {
        return switch (vista) {
            case PENDIENTES -> enEstados(ABIERTAS);
            case SIN_PROGRAMAR -> enEstados(ABIERTAS).and(programada(false));
            case PROGRAMADAS -> enEstados(ABIERTAS).and(fechaEstimadaAntesDe(hoy, false));
            case RETRASADAS -> enEstados(ABIERTAS).and(fechaEstimadaAntesDe(hoy, true));
            case PARCIALES -> enEstados(EnumSet.of(EstadoOrdenCompra.RECIBIDA_PARCIALMENTE));
            case COMPLETAS -> enEstados(EnumSet.of(EstadoOrdenCompra.RECIBIDA));
            case CON_INCIDENCIAS -> enEstados(EMITIDAS).and(conIncidencias());
            case INTERVENCION -> requiereIntervencion(hoy);
            case TODAS -> enEstados(EMITIDAS);
        };
    }

    private static EntregaDTO aDTO(OrdenCompra orden, LocalDate hoy, RecepcionRepository.ResumenPorOrden resumen) {
        return resumen == null
                ? EntregaDTO.from(orden, hoy, 0, null, 0)
                : EntregaDTO.from(orden, hoy, resumen.getRecepciones(), resumen.getUltima(), resumen.getIncidencias());
    }

    private static boolean esGestor() {
        return UsuarioActual.obtener().getRol().esGestor();
    }
}
