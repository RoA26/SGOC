package com.unisen.sgp.service;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.exception.ConflictoException;
import com.unisen.sgp.exception.RecursoNoEncontradoException;
import com.unisen.sgp.model.dto.IncidenciaRecepcionDTO;
import com.unisen.sgp.model.dto.LineaRecepcionDTO;
import com.unisen.sgp.model.dto.RecepcionRequestDTO;
import com.unisen.sgp.model.dto.RecepcionResponseDTO;
import com.unisen.sgp.model.entity.DetalleOrdenCompra;
import com.unisen.sgp.model.entity.DetalleRecepcion;
import com.unisen.sgp.model.entity.Existencia;
import com.unisen.sgp.model.entity.MovimientoInventario;
import com.unisen.sgp.model.entity.OrdenCompra;
import com.unisen.sgp.model.entity.Recepcion;
import com.unisen.sgp.model.entity.TipoIncidencia;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.MovimientoInventarioRepository;
import com.unisen.sgp.repository.OrdenCompraRepository;
import com.unisen.sgp.repository.RecepcionRepository;
import com.unisen.sgp.repository.RecepcionRepository.Filtros;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.security.UsuarioActual;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recepciones de mercancía de las órdenes emitidas. Una recepción, sus líneas, las unidades
 * recibidas de la orden, las existencias y los movimientos del kárdex se guardan en una sola
 * transacción: o todo o nada.
 *
 * <p>Concurrencia: primero se bloquea la orden (dos recepciones de la misma orden se
 * serializan y la segunda ve lo pendiente real) y después las existencias en orden de producto
 * (ver {@link InventarioService#bloquear}). Idempotencia: la clave del cliente es única por
 * empresa; repetirla devuelve la recepción ya registrada.
 */
@Service
@Transactional(readOnly = true)
public class RecepcionService {

    private static final Logger log = LoggerFactory.getLogger(RecepcionService.class);

    private final RecepcionRepository recepcionRepository;
    private final OrdenCompraRepository ordenRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final InventarioService inventarioService;
    private final UsuarioRepository usuarioRepository;
    private final CalendarioNegocio calendario;
    private final Clock clock;

    public RecepcionService(RecepcionRepository recepcionRepository, OrdenCompraRepository ordenRepository,
                            MovimientoInventarioRepository movimientoRepository, InventarioService inventarioService,
                            UsuarioRepository usuarioRepository, CalendarioNegocio calendario, Clock clock) {
        this.calendario = calendario;
        this.recepcionRepository = recepcionRepository;
        this.ordenRepository = ordenRepository;
        this.movimientoRepository = movimientoRepository;
        this.inventarioService = inventarioService;
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
    }

    /** Filtros del listado de recepciones (todos opcionales). */
    public record Filtro(LocalDate desde, LocalDate hasta, Long proveedorId, Long ordenId, Long registradaPorId,
                         Boolean conIncidencias) {

        public static Filtro ninguno() {
            return new Filtro(null, null, null, null, null, null);
        }
    }

    /** Supervisión: todas las recepciones de la empresa, con costos, quién las registró e incidencias. */
    @PreAuthorize(Permisos.GESTION)
    public Page<RecepcionResponseDTO> listar(Filtro filtro, Pageable pageable) {
        return buscar(filtro, pageable).map(RecepcionResponseDTO::from);
    }

    /**
     * Historial personal: las recepciones que registró quien pregunta (la identidad sale del
     * token, nunca de un parámetro). Sin costos para el trabajador.
     */
    @PreAuthorize(Permisos.MIEMBRO_EMPRESA)
    public Page<RecepcionResponseDTO> mias(Filtro filtro, Pageable pageable) {
        var usuario = UsuarioActual.obtener();
        Filtro propio = new Filtro(filtro.desde(), filtro.hasta(), filtro.proveedorId(), filtro.ordenId(),
                usuario.getId(), filtro.conIncidencias());
        boolean verCostos = usuario.getRol().esGestor();
        return buscar(propio, pageable).map(r -> RecepcionResponseDTO.from(r).para(verCostos));
    }

    /** Detalle de una recepción (404 si no es de la empresa). El trabajador la ve sin costos. */
    @PreAuthorize(Permisos.LECTURA_OPERATIVA)
    public RecepcionResponseDTO obtener(Long id) {
        Recepcion recepcion = recepcionRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.recepcion(id));
        return RecepcionResponseDTO.from(recepcion).para(UsuarioActual.obtener().getRol().esGestor());
    }

    private Page<Recepcion> buscar(Filtro filtro, Pageable pageable) {
        var intervalo = calendario.intervalo(filtro.desde(), filtro.hasta(), CalendarioNegocio.DIAS_MAXIMOS);
        return recepcionRepository.findAll(Filtros.desde(intervalo.inicio())
                .and(Filtros.antesDe(intervalo.fin()))
                .and(Filtros.deOrden(filtro.ordenId()))
                .and(Filtros.deProveedor(filtro.proveedorId()))
                .and(Filtros.registradaPor(filtro.registradaPorId()))
                .and(Filtros.conIncidencias(filtro.conIncidencias())), pageable);
    }

    /** Historial de recepciones de una orden (404 si la orden no es de la empresa). */
    @PreAuthorize(Permisos.GESTION)
    public List<RecepcionResponseDTO> deOrden(Long ordenId) {
        if (!ordenRepository.existsById(ordenId)) {
            throw RecursoNoEncontradoException.ordenCompra(ordenId);
        }
        return recepcionRepository.findByOrdenIdOrderByRecibidaEnAscIdAsc(ordenId).stream()
                .map(RecepcionResponseDTO::from)
                .toList();
    }

    /**
     * Registra una recepción parcial o total, con sus incidencias. La puede registrar el
     * administrador o un trabajador de la empresa (la recepción operativa); el SUPER_ADMIN no
     * (supervisa en solo lectura). Quién, su empresa y cuándo los fija el servidor.
     *
     * <p>Reglas: nunca se acepta más de lo pendiente (un excedente se informa como incidencia y
     * no entra al inventario); aceptado + dañado + faltante de una línea no supera lo pendiente;
     * hace falta al menos una línea aceptada o una incidencia.
     *
     * @throws ConflictoException     si la orden no admite recepciones o la clave ya se usó con otros datos
     * @throws CampoInvalidoException si una línea o incidencia no es de la orden, se repite o supera lo pendiente
     */
    @PreAuthorize(Permisos.MIEMBRO_EMPRESA)
    @Transactional
    public ResultadoIdempotente<RecepcionResponseDTO> registrar(Long ordenId, RecepcionRequestDTO dto) {
        TenantContextHolder.requerirEmpresa();
        boolean verCostos = UsuarioActual.obtener().getRol().esGestor();
        OrdenCompra orden = ordenRepository.findWithLockById(ordenId)
                .orElseThrow(() -> RecursoNoEncontradoException.ordenCompra(ordenId));

        var previa = recepcionRepository.findByClaveIdempotencia(dto.claveIdempotencia());
        if (previa.isPresent()) {
            Recepcion recepcion = previa.get();
            if (!recepcion.getOrden().getId().equals(ordenId) || !mismaPeticion(recepcion, dto)) {
                throw new ConflictoException("La clave de idempotencia ya se usó en otra recepción con datos distintos.");
            }
            return new ResultadoIdempotente<>(RecepcionResponseDTO.from(recepcion).para(verCostos), false);
        }

        orden.exigirQueAdmitaRecepcion();
        if (dto.detalles().isEmpty() && dto.incidencias().isEmpty()) {
            throw new CampoInvalidoException("detalles",
                    "Indica al menos una línea recibida o una incidencia de la entrega.");
        }
        Map<Long, DetalleOrdenCompra> lineasOrden = orden.getDetalles().stream()
                .collect(Collectors.toMap(DetalleOrdenCompra::getId, Function.identity()));
        Map<Long, Integer> recibido = new HashMap<>();
        for (int i = 0; i < dto.detalles().size(); i++) {
            LineaRecepcionDTO linea = dto.detalles().get(i);
            DetalleOrdenCompra lineaOrden = lineasOrden.get(linea.detalleOrdenId());
            if (lineaOrden == null) {
                throw new CampoInvalidoException("detalles[" + i + "].detalleOrdenId", "La línea no pertenece a esta orden.");
            }
            if (recibido.putIfAbsent(lineaOrden.getId(), linea.cantidad()) != null) {
                throw new CampoInvalidoException("detalles[" + i + "].detalleOrdenId",
                        "Esta línea ya está en la recepción: indica la cantidad una sola vez.");
            }
            if (linea.cantidad() > lineaOrden.pendiente()) {
                throw new CampoInvalidoException("detalles[" + i + "].cantidad", lineaOrden.pendiente() == 0
                        ? "«" + lineaOrden.getProducto().getNombre() + "» ya se recibió completo."
                        : "Solo quedan " + lineaOrden.pendiente() + " unidades pendientes de «"
                        + lineaOrden.getProducto().getNombre() + "». Si llegaron de más, infórmalo como excedente.");
            }
        }
        validarIncidencias(dto.incidencias(), lineasOrden, recibido);

        Instant ahora = ahora();
        Usuario usuario = usuarioRepository.getReferenceById(UsuarioActual.obtener().getId());
        Recepcion recepcion = new Recepcion(orden, dto.claveIdempotencia(), usuario, ahora, dto.observaciones());
        List<DetalleRecepcion> lineas = new ArrayList<>();
        for (LineaRecepcionDTO linea : dto.detalles()) {
            lineas.add(recepcion.registrar(lineasOrden.get(linea.detalleOrdenId()), linea.cantidad()));
        }
        for (IncidenciaRecepcionDTO incidencia : dto.incidencias()) {
            recepcion.reportar(incidencia.tipo(),
                    incidencia.detalleOrdenId() == null ? null : lineasOrden.get(incidencia.detalleOrdenId()),
                    incidencia.cantidad(), incidencia.descripcion());
        }
        // Una clave repetida en paralelo (con otra orden) choca aquí con uq_recepciones_clave → 409.
        recepcionRepository.saveAndFlush(recepcion);

        if (!lineas.isEmpty()) {
            Map<Long, Existencia> existencias = inventarioService.bloquear(lineas.stream()
                    .map(l -> l.getDetalleOrden().getProducto().getId())
                    .toList());
            for (DetalleRecepcion linea : lineas) {
                Existencia existencia = existencias.get(linea.getDetalleOrden().getProducto().getId());
                int saldo = existencia.entrada(linea.getCantidad(), linea.getDetalleOrden().getCostoUnitario(), ahora);
                movimientoRepository.save(MovimientoInventario.deRecepcion(linea, saldo, usuario, ahora));
            }
        }
        orden.actualizarEstadoPorRecepcion();
        ordenRepository.saveAndFlush(orden);
        log.info("Recepción de la orden {} ({} líneas, {} incidencias) registrada por {}; estado: {}", orden.getNumero(),
                lineas.size(), dto.incidencias().size(), UsuarioActual.paraAuditoria(), orden.getEstado());
        return new ResultadoIdempotente<>(RecepcionResponseDTO.from(recepcion).para(verCostos), true);
    }

    /**
     * Cada incidencia por línea apunta a una línea de esta orden y no se repite (mismo tipo y
     * línea). Aceptado + dañado + faltante de una línea no supera lo pendiente: son unidades de
     * lo que se esperaba. El excedente, por definición, va más allá y no entra al inventario.
     */
    private static void validarIncidencias(List<IncidenciaRecepcionDTO> incidencias,
                                           Map<Long, DetalleOrdenCompra> lineasOrden, Map<Long, Integer> recibido) {
        Map<Long, Integer> esperadas = new HashMap<>(recibido);
        Set<String> vistas = new HashSet<>();
        for (int i = 0; i < incidencias.size(); i++) {
            IncidenciaRecepcionDTO incidencia = incidencias.get(i);
            String campo = "incidencias[" + i + "]";
            DetalleOrdenCompra lineaOrden = null;
            if (incidencia.detalleOrdenId() != null) {
                lineaOrden = lineasOrden.get(incidencia.detalleOrdenId());
                if (lineaOrden == null) {
                    throw new CampoInvalidoException(campo + ".detalleOrdenId", "La línea no pertenece a esta orden.");
                }
            }
            if (incidencia.tipo().porLinea()) {
                if (lineaOrden == null) {
                    throw new CampoInvalidoException(campo + ".detalleOrdenId", "Indica el producto de la orden afectado.");
                }
                if (incidencia.cantidad() == null) {
                    throw new CampoInvalidoException(campo + ".cantidad", "Indica cuántas unidades.");
                }
                if (!vistas.add(incidencia.tipo() + "/" + lineaOrden.getId())) {
                    throw new CampoInvalidoException(campo + ".tipo",
                            "Esta incidencia ya está informada para ese producto: indica la cantidad una sola vez.");
                }
                if (incidencia.tipo() != TipoIncidencia.EXCEDENTE) {
                    int total = esperadas.merge(lineaOrden.getId(), incidencia.cantidad(), Integer::sum);
                    if (total > lineaOrden.pendiente()) {
                        throw new CampoInvalidoException(campo + ".cantidad", "Recibidas, dañadas y faltantes de «"
                                + lineaOrden.getProducto().getNombre() + "» suman " + total + " y solo quedan "
                                + lineaOrden.pendiente() + " pendientes.");
                    }
                }
            } else if (incidencia.descripcion() == null) {
                throw new CampoInvalidoException(campo + ".descripcion", "Describe la incidencia.");
            }
        }
    }

    /** Idempotencia: la misma clave solo vale con las mismas líneas y las mismas incidencias. */
    private static boolean mismaPeticion(Recepcion recepcion, RecepcionRequestDTO dto) {
        Map<Long, Integer> registradas = recepcion.getDetalles().stream()
                .collect(Collectors.toMap(d -> d.getDetalleOrden().getId(), DetalleRecepcion::getCantidad));
        Map<Long, Integer> pedidas = new HashMap<>();
        for (LineaRecepcionDTO linea : dto.detalles()) {
            if (pedidas.put(linea.detalleOrdenId(), linea.cantidad()) != null) {
                return false;
            }
        }
        List<String> incidenciasRegistradas = recepcion.getIncidencias().stream()
                .map(i -> huella(i.getTipo(), i.getDetalleOrden() == null ? null : i.getDetalleOrden().getId(),
                        i.getCantidad(), i.getDescripcion()))
                .sorted().toList();
        List<String> incidenciasPedidas = dto.incidencias().stream()
                .map(i -> huella(i.tipo(), i.detalleOrdenId(), i.cantidad(), i.descripcion()))
                .sorted().toList();
        return registradas.equals(pedidas) && incidenciasRegistradas.equals(incidenciasPedidas);
    }

    private static String huella(TipoIncidencia tipo, Long linea, Integer cantidad, String descripcion) {
        return tipo + "|" + linea + "|" + cantidad + "|" + descripcion;
    }

    private Instant ahora() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
