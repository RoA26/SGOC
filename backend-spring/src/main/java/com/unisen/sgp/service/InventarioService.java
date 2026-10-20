package com.unisen.sgp.service;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.exception.ConflictoException;
import com.unisen.sgp.model.dto.AjusteInventarioDTO;
import com.unisen.sgp.model.dto.ExistenciaDTO;
import com.unisen.sgp.model.dto.MovimientoInventarioDTO;
import com.unisen.sgp.model.entity.EstadoStock;
import com.unisen.sgp.model.entity.Existencia;
import com.unisen.sgp.model.entity.MovimientoInventario;
import com.unisen.sgp.model.entity.ProductoReferencia;
import com.unisen.sgp.repository.ExistenciaRepository;
import com.unisen.sgp.repository.ExistenciaRepository.Filtros;
import com.unisen.sgp.repository.MovimientoInventarioRepository;
import com.unisen.sgp.repository.ProductoReferenciaRepository;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.security.UsuarioActual;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Existencias y kárdex de la empresa actual (@TenantId).
 *
 * <p>Toda variación de existencias pasa por una fila de {@code existencias} bloqueada
 * ({@code FOR UPDATE}) y deja un {@link MovimientoInventario} con el saldo resultante, en la
 * misma transacción: dos operaciones simultáneas sobre el mismo producto se serializan y el
 * saldo nunca queda negativo (ck_existencias_cantidad lo garantiza también en la BD).
 */
@Service
@Transactional(readOnly = true)
public class InventarioService {

    private static final Logger log = LoggerFactory.getLogger(InventarioService.class);

    private final ExistenciaRepository existenciaRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final ProductoReferenciaRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    public InventarioService(ExistenciaRepository existenciaRepository,
                             MovimientoInventarioRepository movimientoRepository,
                             ProductoReferenciaRepository productoRepository, UsuarioRepository usuarioRepository,
                             Clock clock) {
        this.existenciaRepository = existenciaRepository;
        this.movimientoRepository = movimientoRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
    }

    /** Existencias de los productos activos; toda persona de la empresa puede consultarlas (sin costos). */
    public Page<ExistenciaDTO> listar(EstadoStock estado, String texto, Pageable pageable) {
        Specification<Existencia> filtro = Filtros.deProductosActivos().and(Filtros.conTexto(texto));
        if (estado != null) {
            filtro = filtro.and(switch (estado) {
                case AGOTADO -> Filtros.agotadas();
                case CRITICO -> Filtros.criticas();
                case NORMAL -> Filtros.normales();
            });
        }
        return existenciaRepository.findAll(filtro, pageable).map(ExistenciaDTO::from);
    }

    /** Kárdex (con costos): solo gestión. */
    @PreAuthorize(Permisos.GESTION)
    public Page<MovimientoInventarioDTO> movimientos(Long productoId, Pageable pageable) {
        return movimientoRepository.findAll(MovimientoInventarioRepository.Filtros.deProducto(productoId), pageable)
                .map(MovimientoInventarioDTO::from);
    }

    /**
     * Ajuste manual con motivo. Idempotente por {@code claveIdempotencia}: repetir la petición con
     * la misma clave y los mismos datos devuelve el movimiento ya registrado; con otros datos, 409.
     *
     * @throws ConflictoException si las existencias quedarían negativas
     */
    @PreAuthorize(Permisos.ADMINISTRADOR_COMERCIAL)
    @Transactional
    public ResultadoIdempotente<MovimientoInventarioDTO> ajustar(AjusteInventarioDTO dto) {
        TenantContextHolder.requerirEmpresa();
        if (dto.cantidad() == 0) {
            throw new CampoInvalidoException("cantidad", "La cantidad no puede ser 0.");
        }
        if (dto.costoUnitario() != null && dto.cantidad() < 0) {
            throw new CampoInvalidoException("costoUnitario", "Solo las entradas (cantidad positiva) llevan costo.");
        }
        ProductoReferencia producto = productoRepository.findById(dto.productoId())
                .filter(ProductoReferencia::isActivo)
                .orElseThrow(() -> new CampoInvalidoException("productoId", "El producto no existe o fue dado de baja."));

        // Primero el bloqueo: dos peticiones con la misma clave se serializan y la segunda ve la primera.
        Existencia existencia = bloquear(List.of(producto.getId())).get(producto.getId());
        var previo = movimientoRepository.findByClaveIdempotencia(dto.claveIdempotencia());
        if (previo.isPresent()) {
            MovimientoInventario movimiento = previo.get();
            boolean mismaPeticion = movimiento.getProducto().getId().equals(producto.getId())
                    && movimiento.getCantidad() == dto.cantidad()
                    && Objects.equals(movimiento.getMotivo(), dto.motivo());
            if (!mismaPeticion) {
                throw new ConflictoException("La clave de idempotencia ya se usó en otro ajuste con datos distintos.");
            }
            return new ResultadoIdempotente<>(MovimientoInventarioDTO.from(movimiento), false);
        }

        if (existencia.getCantidad() + dto.cantidad() < 0) {
            throw new ConflictoException("Existencias insuficientes: «" + producto.getNombre() + "» tiene "
                    + existencia.getCantidad() + " unidades y no se pueden retirar " + (-dto.cantidad()) + ".");
        }
        Instant ahora = ahora();
        // Las entradas actualizan el costo promedio (sin costo: se valoran al promedio vigente).
        int saldo = dto.cantidad() > 0 ? existencia.entrada(dto.cantidad(), dto.costoUnitario(), ahora)
                : existencia.aplicar(dto.cantidad(), ahora);
        MovimientoInventario movimiento = movimientoRepository.saveAndFlush(MovimientoInventario.deAjuste(producto,
                dto.cantidad(), saldo, dto.costoUnitario(), dto.motivo(), dto.claveIdempotencia(),
                usuarioRepository.getReferenceById(UsuarioActual.obtener().getId()), ahora));
        log.info("Ajuste de inventario de {} unidades en el producto {} por {}", dto.cantidad(), producto.getSku(),
                UsuarioActual.paraAuditoria());
        return new ResultadoIdempotente<>(MovimientoInventarioDTO.from(movimiento), true);
    }

    /**
     * Bloquea las existencias de los productos, siempre en orden de id de producto (evita
     * interbloqueos entre operaciones con productos en común). Si a un producto le faltara su
     * fila (no debería: se crea con él y V11 la creó para los existentes), se crea aquí; la
     * restricción uq_existencias_producto impide duplicarla.
     */
    public Map<Long, Existencia> bloquear(Collection<Long> productoIds) {
        Map<Long, Existencia> filas = existenciaRepository.findWithLockByProductoIdInOrderByProductoIdAsc(productoIds)
                .stream().collect(Collectors.toMap(Existencia::getProductoId, Function.identity()));
        for (Long productoId : productoIds) {
            if (!filas.containsKey(productoId)) {
                log.warn("Producto {} sin fila de existencias: se crea", productoId);
                filas.put(productoId, existenciaRepository.saveAndFlush(new Existencia(productoId, ahora())));
            }
        }
        return filas;
    }

    private Instant ahora() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
