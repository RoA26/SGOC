package com.unisen.sgp.service.comercial;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.exception.ConflictoException;
import com.unisen.sgp.exception.RecursoNoEncontradoException;
import com.unisen.sgp.model.dto.VentaDTO;
import com.unisen.sgp.model.dto.VentaDTO.AnulacionRequest;
import com.unisen.sgp.model.dto.VentaDTO.Detalle;
import com.unisen.sgp.model.dto.VentaDTO.DevolucionRequest;
import com.unisen.sgp.model.dto.VentaDTO.LineaDevolucionRequest;
import com.unisen.sgp.model.entity.DetalleDevolucionVenta;
import com.unisen.sgp.model.entity.DetalleVenta;
import com.unisen.sgp.model.entity.DevolucionVenta;
import com.unisen.sgp.model.entity.EstadoVenta;
import com.unisen.sgp.model.entity.Existencia;
import com.unisen.sgp.model.entity.MovimientoInventario;
import com.unisen.sgp.model.entity.TipoDevolucion;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.model.entity.Venta;
import com.unisen.sgp.repository.DevolucionVentaRepository;
import com.unisen.sgp.repository.MovimientoInventarioRepository;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.repository.VentaRepository;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.security.UsuarioActual;
import com.unisen.sgp.service.CalendarioNegocio;
import com.unisen.sgp.service.InventarioService;
import com.unisen.sgp.service.ResultadoIdempotente;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Devoluciones y anulaciones de ventas: nunca se editan ni se borran líneas o movimientos.
 *
 * <ul>
 *   <li><b>Devolución</b> (parcial o total, cualquier día): unidades de una o varias líneas, sin
 *       superar lo que queda por devolver. Cada línea indica si la mercancía vuelve al inventario
 *       vendible (movimiento DEVOLUCION_VENTA al costo histórico) o no (p. ej. dañada: no se
 *       mueve inventario). El importe devuelto es la parte proporcional de la base y del IVA de la
 *       línea, repartida de forma que la suma de todas las devoluciones de una línea sea
 *       exactamente lo vendido.</li>
 *   <li><b>Anulación</b> (solo el mismo día y sin devoluciones previas): corrige un error de
 *       registro. Revierte todas las unidades al inventario y la venta deja de contarse.</li>
 * </ul>
 * Solo el administrador comercial; la venta se bloquea (dos devoluciones simultáneas se
 * serializan) y la clave de idempotencia impide registrar dos veces la misma.
 */
@Service
@Transactional(readOnly = true)
public class DevolucionVentaService {

    private static final Logger log = LoggerFactory.getLogger(DevolucionVentaService.class);

    private final VentaRepository ventaRepository;
    private final DevolucionVentaRepository devolucionRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final InventarioService inventarioService;
    private final VentaService ventaService;
    private final CalendarioNegocio calendario;
    private final Clock clock;

    public DevolucionVentaService(VentaRepository ventaRepository, DevolucionVentaRepository devolucionRepository,
                                  MovimientoInventarioRepository movimientoRepository,
                                  UsuarioRepository usuarioRepository, InventarioService inventarioService,
                                  VentaService ventaService, CalendarioNegocio calendario, Clock clock) {
        this.ventaRepository = ventaRepository;
        this.devolucionRepository = devolucionRepository;
        this.movimientoRepository = movimientoRepository;
        this.usuarioRepository = usuarioRepository;
        this.inventarioService = inventarioService;
        this.ventaService = ventaService;
        this.calendario = calendario;
        this.clock = clock;
    }

    @PreAuthorize(Permisos.ADMINISTRADOR_COMERCIAL)
    @Transactional
    public ResultadoIdempotente<Detalle> anular(Long ventaId, AnulacionRequest dto) {
        TenantContextHolder.requerirEmpresa();
        Venta venta = ventaRepository.findWithLockById(ventaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("venta", ventaId));
        Optional<ResultadoIdempotente<Detalle>> repetida = repetida(dto.claveIdempotencia(), venta, TipoDevolucion.ANULACION);
        if (repetida.isPresent()) {
            return repetida.get();
        }
        if (venta.getEstado() != EstadoVenta.CONFIRMADA) {
            throw new ConflictoException(venta.getEstado() == EstadoVenta.ANULADA ? "La venta ya está anulada."
                    : "La venta tiene devoluciones: registra la devolución de lo que queda en lugar de anularla.");
        }
        Instant ahora = ahora();
        if (!calendario.hoy().equals(venta.getVendidaEn().atZone(calendario.zona()).toLocalDate())) {
            throw new ConflictoException("Solo se anula una venta el mismo día en que se registró. "
                    + "Para una venta de otro día, registra una devolución.");
        }
        Usuario usuario = usuarioActual();
        DevolucionVenta anulacion = new DevolucionVenta(venta, TipoDevolucion.ANULACION, dto.claveIdempotencia(),
                dto.motivo(), usuario, ahora);
        for (DetalleVenta linea : venta.getDetalles()) {
            anulacion.agregar(new DetalleDevolucionVenta(linea, linea.getCantidad(), true, linea.getBaseGravable(),
                    linea.getImpuesto()));
            linea.devolver(linea.getCantidad());
        }
        venta.anular(usuario, dto.motivo(), ahora);
        registrar(anulacion, usuario, ahora);
        log.info("Venta {} anulada por {}: {}", VentaDTO.numeroVisible(venta.getNumero()), UsuarioActual.paraAuditoria(),
                dto.motivo());
        return new ResultadoIdempotente<>(ventaService.detalle(venta), true);
    }

    @PreAuthorize(Permisos.ADMINISTRADOR_COMERCIAL)
    @Transactional
    public ResultadoIdempotente<Detalle> devolver(Long ventaId, DevolucionRequest dto) {
        TenantContextHolder.requerirEmpresa();
        Venta venta = ventaRepository.findWithLockById(ventaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("venta", ventaId));
        Optional<ResultadoIdempotente<Detalle>> repetida = repetida(dto.claveIdempotencia(), venta, TipoDevolucion.DEVOLUCION);
        if (repetida.isPresent()) {
            return repetida.get();
        }
        if (venta.getEstado() == EstadoVenta.ANULADA || venta.getEstado() == EstadoVenta.DEVUELTA) {
            throw new ConflictoException(venta.getEstado() == EstadoVenta.ANULADA ? "La venta está anulada."
                    : "Ya se devolvieron todas las unidades de la venta.");
        }
        Map<Long, DetalleVenta> lineas = venta.getDetalles().stream()
                .collect(Collectors.toMap(DetalleVenta::getId, Function.identity()));
        Usuario usuario = usuarioActual();
        Instant ahora = ahora();
        DevolucionVenta devolucion = new DevolucionVenta(venta, TipoDevolucion.DEVOLUCION, dto.claveIdempotencia(),
                dto.motivo(), usuario, ahora);
        Set<Long> vistas = new HashSet<>();
        for (int i = 0; i < dto.lineas().size(); i++) {
            LineaDevolucionRequest pedida = dto.lineas().get(i);
            DetalleVenta linea = lineas.get(pedida.detalleVentaId());
            if (linea == null) {
                throw new CampoInvalidoException("lineas[" + i + "].detalleVentaId", "La línea no pertenece a esta venta.");
            }
            if (!vistas.add(linea.getId())) {
                throw new CampoInvalidoException("lineas[" + i + "].detalleVentaId", "La línea está repetida.");
            }
            int pendiente = linea.pendienteDeDevolver();
            if (pedida.cantidad() > pendiente) {
                throw new CampoInvalidoException("lineas[" + i + "].cantidad", "De «" + linea.getNombre()
                        + "» solo quedan " + pendiente + " unidades por devolver.");
            }
            // Reparto telescópico: lo devuelto acumulado hasta k unidades es siempre round(importe × k / n),
            // así la suma de todas las devoluciones de una línea es exactamente su importe y nunca negativa.
            int previas = linea.getCantidadDevuelta();
            int despues = previas + pedida.cantidad();
            BigDecimal base = proporcion(linea.getBaseGravable(), despues, linea.getCantidad())
                    .subtract(proporcion(linea.getBaseGravable(), previas, linea.getCantidad()));
            BigDecimal impuesto = proporcion(linea.getImpuesto(), despues, linea.getCantidad())
                    .subtract(proporcion(linea.getImpuesto(), previas, linea.getCantidad()));
            devolucion.agregar(new DetalleDevolucionVenta(linea, pedida.cantidad(), pedida.reingresaInventario(), base,
                    impuesto));
            linea.devolver(pedida.cantidad());
        }
        venta.actualizarEstadoPorDevolucion();
        registrar(devolucion, usuario, ahora);
        log.info("Devolución en la venta {} por {}: {} líneas, total {}", VentaDTO.numeroVisible(venta.getNumero()),
                UsuarioActual.paraAuditoria(), devolucion.getDetalles().size(), devolucion.getTotal());
        return new ResultadoIdempotente<>(ventaService.detalle(venta), true);
    }

    /** Guarda la devolución y repone en el inventario las unidades que reingresan, con su movimiento. */
    private void registrar(DevolucionVenta devolucion, Usuario usuario, Instant ahora) {
        devolucionRepository.saveAndFlush(devolucion);
        List<DetalleDevolucionVenta> reingresan = devolucion.getDetalles().stream()
                .filter(DetalleDevolucionVenta::isReingresaInventario).toList();
        if (!reingresan.isEmpty()) {
            Map<Long, Existencia> existencias = inventarioService.bloquear(reingresan.stream()
                    .map(d -> d.getDetalleVenta().getProducto().getId()).distinct().sorted().toList());
            for (DetalleDevolucionVenta d : reingresan) {
                Existencia existencia = existencias.get(d.getDetalleVenta().getProducto().getId());
                int saldo = existencia.entrada(d.getCantidad(), d.getCostoUnitario(), ahora);
                movimientoRepository.save(MovimientoInventario.deDevolucion(d, saldo, usuario, ahora));
            }
        }
        ventaRepository.saveAndFlush(devolucion.getVenta());
        movimientoRepository.flush();
    }

    /**
     * Misma clave ya registrada: si fue en esta venta y del mismo tipo, devuelve el estado actual
     * (reintento); si fue en otra operación, 409.
     */
    private Optional<ResultadoIdempotente<Detalle>> repetida(String clave, Venta venta, TipoDevolucion tipo) {
        return devolucionRepository.findByClaveIdempotencia(clave).map(previa -> {
            if (!previa.getVenta().getId().equals(venta.getId()) || previa.getTipo() != tipo) {
                throw new ConflictoException("La clave de idempotencia ya se usó en otra operación.");
            }
            return new ResultadoIdempotente<>(ventaService.detalle(venta), false);
        });
    }

    static BigDecimal proporcion(BigDecimal importe, int parte, int total) {
        return Dinero.redondear(importe.multiply(BigDecimal.valueOf(parte))
                .divide(BigDecimal.valueOf(total), Dinero.ESCALA + 6, Dinero.REDONDEO));
    }

    private Usuario usuarioActual() {
        return usuarioRepository.getReferenceById(UsuarioActual.obtener().getId());
    }

    private Instant ahora() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
