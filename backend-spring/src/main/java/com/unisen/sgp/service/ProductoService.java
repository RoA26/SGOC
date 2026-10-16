package com.unisen.sgp.service;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.exception.ConflictoException;
import com.unisen.sgp.exception.RecursoNoEncontradoException;
import com.unisen.sgp.model.dto.ProductoEncontradoDTO;
import com.unisen.sgp.model.dto.ProductoEncontradoDTO.Coincidencia;
import com.unisen.sgp.model.dto.ProductoRequestDTO;
import com.unisen.sgp.model.dto.ProductoResponseDTO;
import com.unisen.sgp.model.entity.EstadoOrdenCompra;
import com.unisen.sgp.model.entity.EstadoSolicitud;
import com.unisen.sgp.model.entity.EstadoStock;
import com.unisen.sgp.model.entity.Existencia;
import com.unisen.sgp.model.entity.Producto;
import com.unisen.sgp.model.entity.ProductoReferencia;
import com.unisen.sgp.model.entity.Proveedor;
import com.unisen.sgp.repository.DetalleOrdenCompraRepository;
import com.unisen.sgp.repository.DetalleSolicitudRepository;
import com.unisen.sgp.repository.ExistenciaRepository;
import com.unisen.sgp.repository.ProductoReferenciaRepository;
import com.unisen.sgp.repository.ProductoRepository;
import com.unisen.sgp.repository.ProveedorRepository;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductoService {

    /** Solicitudes que aún pueden convertirse en compra: sus productos no se pueden dar de baja. */
    private static final Set<EstadoSolicitud> SOLICITUDES_ACTIVAS =
            EnumSet.of(EstadoSolicitud.PENDIENTE, EstadoSolicitud.APROBADA);

    /** Longitud máxima de un código buscado (la del código de barras; el SKU es más corto). */
    private static final int CODIGO_MAX_LENGTH = 64;

    private final ProductoRepository productoRepository;
    private final ProductoReferenciaRepository productoReferenciaRepository;
    private final ProveedorRepository proveedorRepository;
    private final DetalleSolicitudRepository detalleSolicitudRepository;
    private final DetalleOrdenCompraRepository detalleOrdenRepository;
    private final ExistenciaRepository existenciaRepository;
    private final Clock clock;

    public ProductoService(ProductoRepository productoRepository,
                           ProductoReferenciaRepository productoReferenciaRepository,
                           ProveedorRepository proveedorRepository,
                           DetalleSolicitudRepository detalleSolicitudRepository,
                           DetalleOrdenCompraRepository detalleOrdenRepository, ExistenciaRepository existenciaRepository,
                           Clock clock) {
        this.productoRepository = productoRepository;
        this.productoReferenciaRepository = productoReferenciaRepository;
        this.proveedorRepository = proveedorRepository;
        this.detalleSolicitudRepository = detalleSolicitudRepository;
        this.detalleOrdenRepository = detalleOrdenRepository;
        this.existenciaRepository = existenciaRepository;
        this.clock = clock;
    }

    public Page<ProductoResponseDTO> listar(Pageable pageable) {
        return productoRepository.findAll(pageable).map(ProductoResponseDTO::from);
    }

    public ProductoResponseDTO obtener(Long id) {
        return ProductoResponseDTO.from(buscarActivo(id));
    }

    /**
     * Búsqueda por escáner o teclado: código de barras exacto y, si no coincide ninguno, SKU.
     * Siempre dentro de la empresa del contexto, que fija el backend: el cliente no puede elegir
     * otra y en el modo global del SUPER_ADMIN se rechaza (400) en lugar de buscar en todas.
     *
     * <p>Entre las coincidencias se prefiere un producto activo; si solo coincide uno dado de
     * baja, 409 con su nombre (no "no existe": el usuario sabrá por qué no puede usarlo).
     *
     * @throws RecursoNoEncontradoException si ningún producto de la empresa tiene ese código
     */
    public ProductoEncontradoDTO buscarPorCodigo(String codigo) {
        TenantContextHolder.requerirEmpresa();
        String limpio = codigo == null ? "" : codigo.strip();
        if (limpio.isEmpty() || limpio.length() > CODIGO_MAX_LENGTH) {
            throw new CampoInvalidoException("codigo", "Indica un código de barras o SKU de hasta "
                    + CODIGO_MAX_LENGTH + " caracteres.");
        }
        Optional<ProductoReferencia> porCodigoBarras = productoReferenciaRepository.findByCodigoBarras(limpio);
        Optional<ProductoReferencia> porSku = productoReferenciaRepository.findBySku(limpio.toUpperCase(Locale.ROOT));

        Coincidencia coincidencia;
        ProductoReferencia encontrado;
        if (porCodigoBarras.filter(ProductoReferencia::isActivo).isPresent()) {
            coincidencia = Coincidencia.CODIGO_BARRAS;
            encontrado = porCodigoBarras.get();
        } else if (porSku.filter(ProductoReferencia::isActivo).isPresent()) {
            coincidencia = Coincidencia.SKU;
            encontrado = porSku.get();
        } else {
            ProductoReferencia inactivo = porCodigoBarras.or(() -> porSku)
                    .orElseThrow(() -> RecursoNoEncontradoException.productoConCodigo(limpio));
            throw new ConflictoException("«" + inactivo.getNombre() + "» (SKU " + inactivo.getSku()
                    + ") tiene ese código, pero fue dado de baja.");
        }
        Producto producto = buscarActivo(encontrado.getId());
        int unidades = existenciaRepository.findByProductoId(producto.getId()).map(Existencia::getCantidad).orElse(0);
        return new ProductoEncontradoDTO(ProductoResponseDTO.from(producto), unidades,
                EstadoStock.de(unidades, producto.getStockMinimo()), coincidencia);
    }

    /**
     * Crea el producto y su fila de existencias (0 unidades) en la misma transacción: cada
     * producto tiene siempre una fila que bloquear al mover inventario. Un SKU o código de barras
     * duplicado llega como DataIntegrityViolationException (uq_productos_sku,
     * uq_productos_codigo_barras) → 409 con el campo.
     */
    @Transactional
    public ProductoResponseDTO crear(ProductoRequestDTO dto) {
        TenantContextHolder.requerirEmpresa();
        Producto producto = productoRepository.saveAndFlush(new Producto(dto.datos(), proveedorAsignable(dto.proveedorId())));
        existenciaRepository.save(new Existencia(producto.getId(), clock.instant().truncatedTo(ChronoUnit.MICROS)));
        return ProductoResponseDTO.from(producto);
    }

    @Transactional
    public ProductoResponseDTO actualizar(Long id, ProductoRequestDTO dto) {
        TenantContextHolder.requerirEmpresa();
        Producto producto = buscarActivo(id);
        producto.actualizar(dto.datos(), proveedorAsignable(dto.proveedorId()));
        return ProductoResponseDTO.from(productoRepository.saveAndFlush(producto));
    }

    /**
     * Borrado lógico.
     *
     * @throws ConflictoException si el producto está en solicitudes pendientes o aprobadas, en
     *                            órdenes de compra abiertas o le quedan unidades en inventario
     */
    @Transactional
    public void eliminar(Long id) {
        TenantContextHolder.requerirEmpresa();
        Producto producto = buscarActivo(id);
        long solicitudes = detalleSolicitudRepository.countByProductoIdAndSolicitudEstadoIn(id, SOLICITUDES_ACTIVAS);
        if (solicitudes > 0) {
            throw new ConflictoException("No se puede eliminar el producto: está incluido en " + solicitudes
                    + (solicitudes == 1 ? " solicitud pendiente o aprobada." : " solicitudes pendientes o aprobadas."));
        }
        long ordenes = detalleOrdenRepository.countByProductoIdAndOrdenEstadoIn(id, EstadoOrdenCompra.ABIERTAS);
        if (ordenes > 0) {
            throw new ConflictoException("No se puede eliminar el producto: está incluido en " + ordenes
                    + (ordenes == 1 ? " orden de compra abierta." : " órdenes de compra abiertas."));
        }
        int unidades = existenciaRepository.findByProductoId(id).map(Existencia::getCantidad).orElse(0);
        if (unidades > 0) {
            throw new ConflictoException("No se puede eliminar el producto: quedan " + unidades
                    + (unidades == 1 ? " unidad" : " unidades")
                    + " en inventario. Regístralas con un ajuste antes de darlo de baja.");
        }
        productoRepository.delete(producto);
    }

    private Producto buscarActivo(Long id) {
        return productoRepository.findById(id)
                .filter(Producto::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("producto", id));
    }

    /** El proveedor debe existir y estar activo; si no, es un error del campo proveedorId (400). */
    private Proveedor proveedorAsignable(Long proveedorId) {
        return proveedorRepository.findById(proveedorId)
                .filter(Proveedor::isActivo)
                .orElseThrow(() -> new CampoInvalidoException("proveedorId",
                        "El proveedor seleccionado no existe o fue dado de baja."));
    }
}
