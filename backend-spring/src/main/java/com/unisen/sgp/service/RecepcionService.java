package com.unisen.sgp.service;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.exception.ConflictoException;
import com.unisen.sgp.exception.RecursoNoEncontradoException;
import com.unisen.sgp.model.dto.LineaRecepcionDTO;
import com.unisen.sgp.model.dto.RecepcionRequestDTO;
import com.unisen.sgp.model.dto.RecepcionResponseDTO;
import com.unisen.sgp.model.entity.DetalleOrdenCompra;
import com.unisen.sgp.model.entity.DetalleRecepcion;
import com.unisen.sgp.model.entity.Existencia;
import com.unisen.sgp.model.entity.MovimientoInventario;
import com.unisen.sgp.model.entity.OrdenCompra;
import com.unisen.sgp.model.entity.Recepcion;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.MovimientoInventarioRepository;
import com.unisen.sgp.repository.OrdenCompraRepository;
import com.unisen.sgp.repository.RecepcionRepository;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.security.UsuarioActual;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final Clock clock;

    public RecepcionService(RecepcionRepository recepcionRepository, OrdenCompraRepository ordenRepository,
                            MovimientoInventarioRepository movimientoRepository, InventarioService inventarioService,
                            UsuarioRepository usuarioRepository, Clock clock) {
        this.recepcionRepository = recepcionRepository;
        this.ordenRepository = ordenRepository;
        this.movimientoRepository = movimientoRepository;
        this.inventarioService = inventarioService;
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
    }

    @PreAuthorize(Permisos.GESTION)
    public Page<RecepcionResponseDTO> listar(Pageable pageable) {
        return recepcionRepository.findAll(pageable).map(RecepcionResponseDTO::from);
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
     * Registra una recepción parcial o total.
     *
     * @throws ConflictoException     si la orden no admite recepciones o la clave ya se usó con otros datos
     * @throws CampoInvalidoException si una línea no es de la orden, se repite o supera lo pendiente
     */
    @PreAuthorize(Permisos.ADMIN_EMPRESA)
    @Transactional
    public ResultadoIdempotente<RecepcionResponseDTO> registrar(Long ordenId, RecepcionRequestDTO dto) {
        TenantContextHolder.requerirEmpresa();
        OrdenCompra orden = ordenRepository.findWithLockById(ordenId)
                .orElseThrow(() -> RecursoNoEncontradoException.ordenCompra(ordenId));

        var previa = recepcionRepository.findByClaveIdempotencia(dto.claveIdempotencia());
        if (previa.isPresent()) {
            Recepcion recepcion = previa.get();
            if (!recepcion.getOrden().getId().equals(ordenId) || !mismasLineas(recepcion, dto)) {
                throw new ConflictoException("La clave de idempotencia ya se usó en otra recepción con datos distintos.");
            }
            return new ResultadoIdempotente<>(RecepcionResponseDTO.from(recepcion), false);
        }

        orden.exigirQueAdmitaRecepcion();
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
                        + lineaOrden.getProducto().getNombre() + "».");
            }
        }

        Instant ahora = ahora();
        Usuario usuario = usuarioRepository.getReferenceById(UsuarioActual.obtener().getId());
        Recepcion recepcion = new Recepcion(orden, dto.claveIdempotencia(), usuario, ahora, dto.observaciones());
        List<DetalleRecepcion> lineas = new ArrayList<>();
        for (LineaRecepcionDTO linea : dto.detalles()) {
            lineas.add(recepcion.registrar(lineasOrden.get(linea.detalleOrdenId()), linea.cantidad()));
        }
        // Una clave repetida en paralelo (con otra orden) choca aquí con uq_recepciones_clave → 409.
        recepcionRepository.saveAndFlush(recepcion);

        Map<Long, Existencia> existencias = inventarioService.bloquear(lineas.stream()
                .map(l -> l.getDetalleOrden().getProducto().getId())
                .toList());
        for (DetalleRecepcion linea : lineas) {
            Existencia existencia = existencias.get(linea.getDetalleOrden().getProducto().getId());
            int saldo = existencia.aplicar(linea.getCantidad(), ahora);
            movimientoRepository.save(MovimientoInventario.deRecepcion(linea, saldo, usuario, ahora));
        }
        orden.actualizarEstadoPorRecepcion();
        ordenRepository.saveAndFlush(orden);
        log.info("Recepción de la orden {} ({} líneas) registrada por {}; estado: {}", orden.getNumero(), lineas.size(),
                UsuarioActual.paraAuditoria(), orden.getEstado());
        return new ResultadoIdempotente<>(RecepcionResponseDTO.from(recepcion), true);
    }

    private static boolean mismasLineas(Recepcion recepcion, RecepcionRequestDTO dto) {
        Map<Long, Integer> registradas = recepcion.getDetalles().stream()
                .collect(Collectors.toMap(d -> d.getDetalleOrden().getId(), DetalleRecepcion::getCantidad));
        Map<Long, Integer> pedidas = new HashMap<>();
        for (LineaRecepcionDTO linea : dto.detalles()) {
            if (pedidas.put(linea.detalleOrdenId(), linea.cantidad()) != null) {
                return false;
            }
        }
        return registradas.equals(pedidas);
    }

    private Instant ahora() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
