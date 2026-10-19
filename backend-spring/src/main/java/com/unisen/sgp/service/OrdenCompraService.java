package com.unisen.sgp.service;

import static com.unisen.sgp.repository.OrdenCompraRepository.Filtros.conEstado;
import static com.unisen.sgp.repository.OrdenCompraRepository.Filtros.deProveedor;
import static com.unisen.sgp.repository.OrdenCompraRepository.Filtros.deSolicitud;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.exception.ConflictoException;
import com.unisen.sgp.exception.RecursoNoEncontradoException;
import com.unisen.sgp.model.dto.CancelacionOrdenDTO;
import com.unisen.sgp.model.dto.LineaOrdenCompraDTO;
import com.unisen.sgp.model.dto.OrdenCompraEdicionDTO;
import com.unisen.sgp.model.dto.OrdenCompraRequestDTO;
import com.unisen.sgp.model.dto.OrdenCompraResponseDTO;
import com.unisen.sgp.model.entity.DetalleOrdenCompra;
import com.unisen.sgp.model.entity.DetalleSolicitud;
import com.unisen.sgp.model.entity.EstadoOrdenCompra;
import com.unisen.sgp.model.entity.EstadoSolicitud;
import com.unisen.sgp.model.entity.OrdenCompra;
import com.unisen.sgp.model.entity.ProductoReferencia;
import com.unisen.sgp.model.entity.ProveedorReferencia;
import com.unisen.sgp.model.entity.Solicitud;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.EmpresaRepository;
import com.unisen.sgp.repository.OrdenCompraRepository;
import com.unisen.sgp.repository.ProductoReferenciaRepository;
import com.unisen.sgp.repository.ProveedorReferenciaRepository;
import com.unisen.sgp.repository.SolicitudRepository;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.security.UsuarioActual;
import com.unisen.sgp.service.documento.DocumentoOrdenService;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 * Órdenes de compra de la empresa actual. Todas las consultas pasan por el filtro de empresa
 * (@TenantId): un id de otra empresa responde 404 y un proveedor o producto de otra empresa es
 * un campo inválido; las FK compuestas lo impiden también en la BD.
 *
 * <p>Las escrituras son del ADMIN_EMPRESA (el SUPER_ADMIN supervisa en solo lectura) y cada
 * cambio de estado bloquea antes la fila de la orden.
 */
@Service
@Transactional(readOnly = true)
public class OrdenCompraService {

    private static final Logger log = LoggerFactory.getLogger(OrdenCompraService.class);

    private final OrdenCompraRepository ordenRepository;
    private final ProveedorReferenciaRepository proveedorRepository;
    private final ProductoReferenciaRepository productoRepository;
    private final SolicitudRepository solicitudRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final DocumentoOrdenService documentoService;
    private final Clock clock;

    public OrdenCompraService(OrdenCompraRepository ordenRepository, ProveedorReferenciaRepository proveedorRepository,
                              ProductoReferenciaRepository productoRepository, SolicitudRepository solicitudRepository,
                              EmpresaRepository empresaRepository, UsuarioRepository usuarioRepository,
                              DocumentoOrdenService documentoService, Clock clock) {
        this.ordenRepository = ordenRepository;
        this.proveedorRepository = proveedorRepository;
        this.productoRepository = productoRepository;
        this.solicitudRepository = solicitudRepository;
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.documentoService = documentoService;
        this.clock = clock;
    }

    @PreAuthorize(Permisos.GESTION)
    public Page<OrdenCompraResponseDTO> listar(EstadoOrdenCompra estado, Long proveedorId, Long solicitudId,
                                               Pageable pageable) {
        return ordenRepository.findAll(conEstado(estado).and(deProveedor(proveedorId)).and(deSolicitud(solicitudId)),
                pageable).map(OrdenCompraResponseDTO::from);
    }

    @PreAuthorize(Permisos.GESTION)
    public OrdenCompraResponseDTO obtener(Long id) {
        return OrdenCompraResponseDTO.from(buscar(id));
    }

    /**
     * Crea un borrador con sus líneas en una sola transacción. Todas las líneas deben ser
     * productos activos del proveedor elegido; sin costo, se toma el precio actual del producto.
     */
    @PreAuthorize(Permisos.ADMIN_EMPRESA)
    @Transactional
    public OrdenCompraResponseDTO crear(OrdenCompraRequestDTO dto) {
        Long empresaId = TenantContextHolder.requerirEmpresa();
        ProveedorReferencia proveedor = proveedorRepository.findById(dto.proveedorId())
                .filter(ProveedorReferencia::isActivo)
                .orElseThrow(() -> new CampoInvalidoException("proveedorId",
                        "El proveedor seleccionado no existe o fue dado de baja."));
        List<OrdenCompra.Linea> lineas = lineasValidadas(dto.detalles(), proveedor);

        OrdenCompra orden = new OrdenCompra(siguienteNumero(empresaId), proveedor, null, dto.observaciones(),
                usuarioActual());
        orden.definirLineas(lineas);
        OrdenCompra guardada = ordenRepository.saveAndFlush(orden);
        log.info("Orden de compra {} creada en borrador por {}", guardada.getNumero(), UsuarioActual.paraAuditoria());
        return OrdenCompraResponseDTO.from(guardada);
    }

    /** Sustituye observaciones y líneas de un borrador. */
    @PreAuthorize(Permisos.ADMIN_EMPRESA)
    @Transactional
    public OrdenCompraResponseDTO actualizar(Long id, OrdenCompraEdicionDTO dto) {
        TenantContextHolder.requerirEmpresa();
        OrdenCompra orden = buscarConBloqueo(id);
        List<OrdenCompra.Linea> lineas = lineasValidadas(dto.detalles(), orden.getProveedor());
        orden.cambiarObservaciones(dto.observaciones());
        orden.definirLineas(lineas);
        return OrdenCompraResponseDTO.from(ordenRepository.saveAndFlush(orden));
    }

    /**
     * Emite un borrador. Se vuelve a comprobar el catálogo: el proveedor y los productos deben
     * seguir activos y los productos, pertenecer aún a ese proveedor. En la misma transacción se
     * guarda la instantánea del documento (versión 1): o quedan las dos cosas o ninguna.
     */
    @PreAuthorize(Permisos.ADMIN_EMPRESA)
    @Transactional
    public OrdenCompraResponseDTO emitir(Long id) {
        TenantContextHolder.requerirEmpresa();
        OrdenCompra orden = buscarConBloqueo(id);
        if (orden.getEstado() == EstadoOrdenCompra.BORRADOR) {
            comprobarCatalogoVigente(orden);
        }
        Usuario emisor = usuarioActual();
        Instant ahora = ahora();
        orden.emitir(emisor, ahora);
        OrdenCompra emitida = ordenRepository.saveAndFlush(orden);
        documentoService.registrarEmision(emitida, emisor, ahora);
        log.info("Orden de compra {} emitida por {}", emitida.getNumero(), UsuarioActual.paraAuditoria());
        return OrdenCompraResponseDTO.from(emitida);
    }

    @PreAuthorize(Permisos.ADMIN_EMPRESA)
    @Transactional
    public OrdenCompraResponseDTO cancelar(Long id, CancelacionOrdenDTO dto) {
        TenantContextHolder.requerirEmpresa();
        OrdenCompra orden = buscarConBloqueo(id);
        orden.cancelar(usuarioActual(), dto.motivo(), ahora());
        OrdenCompra cancelada = ordenRepository.saveAndFlush(orden);
        log.info("Orden de compra {} cancelada por {}", cancelada.getNumero(), UsuarioActual.paraAuditoria());
        return OrdenCompraResponseDTO.from(cancelada);
    }

    /**
     * Genera los borradores de una solicitud APROBADA: uno por proveedor de sus productos, con
     * las cantidades pedidas y el precio actual como costo (editable antes de emitir).
     *
     * <p>La solicitud se bloquea mientras tanto: dos peticiones simultáneas no pueden generar
     * órdenes duplicadas (la segunda ve las de la primera y recibe 409).
     */
    @PreAuthorize(Permisos.ADMIN_EMPRESA)
    @Transactional
    public List<OrdenCompraResponseDTO> crearDesdeSolicitud(Long solicitudId) {
        Long empresaId = TenantContextHolder.requerirEmpresa();
        Solicitud solicitud = solicitudRepository.findWithLockById(solicitudId)
                .orElseThrow(() -> RecursoNoEncontradoException.solicitud(solicitudId));
        if (solicitud.getEstado() != EstadoSolicitud.APROBADA) {
            throw new ConflictoException("Solo se generan órdenes de solicitudes aprobadas; esta está "
                    + solicitud.getEstado().descripcion() + ".");
        }
        List<OrdenCompra> existentes =
                ordenRepository.findBySolicitudIdAndEstadoNotOrderByNumero(solicitudId, EstadoOrdenCompra.CANCELADA);
        if (!existentes.isEmpty()) {
            throw new ConflictoException("La solicitud ya tiene órdenes de compra: "
                    + existentes.stream().map(o -> numeroVisible(o.getNumero())).collect(Collectors.joining(", "))
                    + ". Cancélalas si quieres generarlas de nuevo.");
        }

        Map<Long, List<DetalleSolicitud>> porProveedor = new LinkedHashMap<>();
        List<String> noDisponibles = new ArrayList<>();
        for (DetalleSolicitud detalle : solicitud.getDetalles()) {
            ProductoReferencia producto = detalle.getProducto();
            if (!producto.isActivo()) {
                noDisponibles.add(producto.getNombre());
            } else {
                porProveedor.computeIfAbsent(producto.getProveedorId(), k -> new ArrayList<>()).add(detalle);
            }
        }
        if (!noDisponibles.isEmpty()) {
            throw new ConflictoException("Hay productos dados de baja en la solicitud: " + String.join(", ", noDisponibles)
                    + ". Crea la orden manualmente con los productos vigentes.");
        }

        Usuario autor = usuarioActual();
        long numero = siguienteNumero(empresaId);
        List<OrdenCompraResponseDTO> creadas = new ArrayList<>();
        for (Map.Entry<Long, List<DetalleSolicitud>> grupo : porProveedor.entrySet()) {
            ProveedorReferencia proveedor = proveedorRepository.findById(grupo.getKey())
                    .filter(ProveedorReferencia::isActivo)
                    .orElseThrow(() -> new ConflictoException(
                            "Un proveedor de la solicitud fue dado de baja. Crea la orden manualmente."));
            OrdenCompra orden = new OrdenCompra(numero++, proveedor, solicitudId,
                    "Generada desde la solicitud #" + solicitudId + ".", autor);
            orden.definirLineas(grupo.getValue().stream()
                    .map(d -> new OrdenCompra.Linea(d.getProducto(), d.getCantidad(), d.getProducto().getPrecio()))
                    .toList());
            creadas.add(OrdenCompraResponseDTO.from(ordenRepository.saveAndFlush(orden)));
        }
        log.info("{} órdenes de compra generadas desde la solicitud {} por {}", creadas.size(), solicitudId,
                UsuarioActual.paraAuditoria());
        return creadas;
    }

    /** Formato visible del número (OC-00042), el mismo que muestra la interfaz. */
    public static String numeroVisible(long numero) {
        return "OC-%05d".formatted(numero);
    }

    /**
     * Siguiente número correlativo de la empresa. Se bloquea la fila de la empresa: dos altas
     * simultáneas no pueden leer el mismo máximo (y uq_ordenes_compra_numero es la red de
     * seguridad).
     */
    private long siguienteNumero(Long empresaId) {
        empresaRepository.findWithLockById(empresaId)
                .orElseThrow(() -> new IllegalStateException("La empresa del contexto no existe: " + empresaId));
        return ordenRepository.ultimoNumero() + 1;
    }

    /**
     * Una consulta para todos los productos (filtrada por empresa): deben existir, estar activos,
     * no repetirse y ser del proveedor de la orden.
     */
    private List<OrdenCompra.Linea> lineasValidadas(List<LineaOrdenCompraDTO> detalles, ProveedorReferencia proveedor) {
        Set<Long> ids = detalles.stream().map(LineaOrdenCompraDTO::productoId).collect(Collectors.toSet());
        Map<Long, ProductoReferencia> productos = productoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ProductoReferencia::getId, Function.identity()));
        Set<Long> yaIncluidos = new HashSet<>();
        List<OrdenCompra.Linea> lineas = new ArrayList<>();
        for (int i = 0; i < detalles.size(); i++) {
            LineaOrdenCompraDTO linea = detalles.get(i);
            ProductoReferencia producto = productos.get(linea.productoId());
            String campo = "detalles[" + i + "].productoId";
            if (producto == null || !producto.isActivo()) {
                throw new CampoInvalidoException(campo, "El producto no existe o fue dado de baja.");
            }
            if (!producto.getProveedorId().equals(proveedor.getId())) {
                throw new CampoInvalidoException(campo, "«" + producto.getNombre() + "» no es de "
                        + proveedor.getRazonSocial() + ": cada orden es de un solo proveedor.");
            }
            if (!yaIncluidos.add(producto.getId())) {
                throw new CampoInvalidoException(campo, "Este producto ya está en otra línea: ajusta allí la cantidad.");
            }
            lineas.add(new OrdenCompra.Linea(producto, linea.cantidad(),
                    linea.costoUnitario() != null ? linea.costoUnitario() : producto.getPrecio()));
        }
        return lineas;
    }

    private static void comprobarCatalogoVigente(OrdenCompra orden) {
        if (!orden.getProveedor().isActivo()) {
            throw new ConflictoException("El proveedor de la orden fue dado de baja: cancela la orden.");
        }
        for (DetalleOrdenCompra detalle : orden.getDetalles()) {
            ProductoReferencia producto = detalle.getProducto();
            if (!producto.isActivo()) {
                throw new ConflictoException("«" + producto.getNombre() + "» fue dado de baja: quítalo de la orden.");
            }
            if (!producto.getProveedorId().equals(orden.getProveedor().getId())) {
                throw new ConflictoException("«" + producto.getNombre() + "» ya no es de "
                        + orden.getProveedor().getRazonSocial() + ": quítalo de la orden.");
            }
        }
    }

    private OrdenCompra buscar(Long id) {
        return ordenRepository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.ordenCompra(id));
    }

    OrdenCompra buscarConBloqueo(Long id) {
        return ordenRepository.findWithLockById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.ordenCompra(id));
    }

    private Usuario usuarioActual() {
        return usuarioRepository.getReferenceById(UsuarioActual.obtener().getId());
    }

    private Instant ahora() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
