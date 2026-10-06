package com.unisen.sgp.service;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.exception.RecursoNoEncontradoException;
import com.unisen.sgp.model.dto.ProductoRequestDTO;
import com.unisen.sgp.model.dto.ProductoResponseDTO;
import com.unisen.sgp.model.entity.Producto;
import com.unisen.sgp.model.entity.Proveedor;
import com.unisen.sgp.repository.ProductoRepository;
import com.unisen.sgp.repository.ProveedorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final ProveedorRepository proveedorRepository;

    public ProductoService(ProductoRepository productoRepository, ProveedorRepository proveedorRepository) {
        this.productoRepository = productoRepository;
        this.proveedorRepository = proveedorRepository;
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

    /** Borrado lógico. */
    @Transactional
    public void eliminar(Long id) {
        productoRepository.delete(buscarActivo(id));
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
