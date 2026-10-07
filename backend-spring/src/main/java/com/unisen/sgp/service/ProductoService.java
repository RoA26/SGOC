package com.unisen.sgp.service;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.exception.ConflictoException;
import com.unisen.sgp.exception.RecursoNoEncontradoException;
import com.unisen.sgp.model.dto.ProductoRequestDTO;
import com.unisen.sgp.model.dto.ProductoResponseDTO;
import com.unisen.sgp.model.entity.EstadoSolicitud;
import com.unisen.sgp.model.entity.Producto;
import com.unisen.sgp.model.entity.Proveedor;
import com.unisen.sgp.repository.DetalleSolicitudRepository;
import com.unisen.sgp.repository.ProductoRepository;
import com.unisen.sgp.repository.ProveedorRepository;
import java.util.EnumSet;
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

    private final ProductoRepository productoRepository;
    private final ProveedorRepository proveedorRepository;
    private final DetalleSolicitudRepository detalleSolicitudRepository;

    public ProductoService(ProductoRepository productoRepository, ProveedorRepository proveedorRepository,
                           DetalleSolicitudRepository detalleSolicitudRepository) {
        this.productoRepository = productoRepository;
        this.proveedorRepository = proveedorRepository;
        this.detalleSolicitudRepository = detalleSolicitudRepository;
    }

    public Page<ProductoResponseDTO> listar(Pageable pageable) {
        return productoRepository.findAll(pageable).map(ProductoResponseDTO::from);
    }

    public ProductoResponseDTO obtener(Long id) {
        return ProductoResponseDTO.from(buscarActivo(id));
    }

    /** Un SKU duplicado llega como DataIntegrityViolationException (uq_productos_sku) → 409. */
    @Transactional
    public ProductoResponseDTO crear(ProductoRequestDTO dto) {
        Producto producto = new Producto(dto.sku(), dto.nombre(), dto.descripcion(), dto.precio(),
                proveedorAsignable(dto.proveedorId()));
        return ProductoResponseDTO.from(productoRepository.saveAndFlush(producto));
    }

    @Transactional
    public ProductoResponseDTO actualizar(Long id, ProductoRequestDTO dto) {
        Producto producto = buscarActivo(id);
        producto.actualizar(dto.sku(), dto.nombre(), dto.descripcion(), dto.precio(),
                proveedorAsignable(dto.proveedorId()));
        return ProductoResponseDTO.from(productoRepository.saveAndFlush(producto));
    }

    /**
     * Borrado lógico.
     *
     * @throws ConflictoException si el producto está en solicitudes pendientes o aprobadas
     */
    @Transactional
    public void eliminar(Long id) {
        Producto producto = buscarActivo(id);
        long solicitudes = detalleSolicitudRepository.countByProductoIdAndSolicitudEstadoIn(id, SOLICITUDES_ACTIVAS);
        if (solicitudes > 0) {
            throw new ConflictoException("No se puede eliminar el producto: está incluido en " + solicitudes
                    + (solicitudes == 1 ? " solicitud pendiente o aprobada." : " solicitudes pendientes o aprobadas."));
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
