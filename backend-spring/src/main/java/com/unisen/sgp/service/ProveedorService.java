package com.unisen.sgp.service;

import com.unisen.sgp.exception.ConflictoException;
import com.unisen.sgp.exception.RecursoNoEncontradoException;
import com.unisen.sgp.model.dto.ProveedorRequestDTO;
import com.unisen.sgp.model.dto.ProveedorResponseDTO;
import com.unisen.sgp.model.entity.Proveedor;
import com.unisen.sgp.repository.ProductoRepository;
import com.unisen.sgp.repository.ProveedorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;

    public ProveedorService(ProveedorRepository proveedorRepository, ProductoRepository productoRepository) {
        this.proveedorRepository = proveedorRepository;
        this.productoRepository = productoRepository;
    }

    public Page<ProveedorResponseDTO> listar(Pageable pageable) {
        return proveedorRepository.findAll(pageable).map(ProveedorResponseDTO::from);
    }

    public ProveedorResponseDTO obtener(Long id) {
        return ProveedorResponseDTO.from(buscarActivo(id));
    }

    /** Un NIT duplicado llega como DataIntegrityViolationException (uq_proveedores_nit) → 409. */
    @Transactional
    public ProveedorResponseDTO crear(ProveedorRequestDTO dto) {
        Proveedor proveedor = new Proveedor(dto.nit(), dto.razonSocial(), dto.email(), dto.telefono(), dto.direccion());
        return ProveedorResponseDTO.from(proveedorRepository.saveAndFlush(proveedor));
    }

    @Transactional
    public ProveedorResponseDTO actualizar(Long id, ProveedorRequestDTO dto) {
        Proveedor proveedor = buscarActivo(id);
        proveedor.actualizar(dto.nit(), dto.razonSocial(), dto.email(), dto.telefono(), dto.direccion());
        // saveAndFlush: un conflicto de NIT se detecta aquí y no al confirmar la transacción.
        return ProveedorResponseDTO.from(proveedorRepository.saveAndFlush(proveedor));
    }

    /**
     * Borrado lógico. Se rechaza si el proveedor tiene productos activos: quedarían
     * apuntando a un proveedor oculto.
     */
    @Transactional
    public void eliminar(Long id) {
        Proveedor proveedor = buscarActivo(id);
        long productosActivos = productoRepository.countByProveedorId(id);
        if (productosActivos > 0) {
            throw new ConflictoException("No se puede eliminar el proveedor: tiene " + productosActivos
                    + (productosActivos == 1 ? " producto activo" : " productos activos")
                    + ". Elimínalos o reasígnalos primero.");
        }
        proveedorRepository.delete(proveedor);
    }

    /** Proveedor activo o {@link RecursoNoEncontradoException}. */
    Proveedor buscarActivo(Long id) {
        return proveedorRepository.findById(id)
                .filter(Proveedor::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("proveedor", id));
    }
}
