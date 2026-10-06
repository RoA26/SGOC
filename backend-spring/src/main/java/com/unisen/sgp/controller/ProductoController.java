package com.unisen.sgp.controller;

import com.unisen.sgp.model.dto.ProductoRequestDTO;
import com.unisen.sgp.model.dto.ProductoResponseDTO;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/productos")
@Tag(name = "Productos")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    @Operation(summary = "Listar productos activos (paginado)",
            description = "Parámetros: page (desde 0), size (máx. 100), sort=campo,asc|desc "
                    + "(admite campos anidados, p. ej. sort=proveedor.razonSocial).")
    public Page<ProductoResponseDTO> listar(
            @ParameterObject @PageableDefault(size = 10, sort = {"nombre", "id"}) Pageable pageable) {
        return productoService.listar(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un producto")
    @ApiResponse(responseCode = "404", description = "No existe o fue dado de baja")
    public ProductoResponseDTO obtener(@PathVariable Long id) {
        return productoService.obtener(id);
    }

    @PostMapping
    @PreAuthorize(Permisos.ADMIN)
    @Operation(summary = "Crear producto")
    @ApiResponse(responseCode = "201", description = "Creado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o proveedor inexistente")
    @ApiResponse(responseCode = "409", description = "SKU duplicado")
    public ResponseEntity<ProductoResponseDTO> crear(@Valid @RequestBody ProductoRequestDTO request) {
        ProductoResponseDTO creado = productoService.crear(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permisos.ADMIN)
    @Operation(summary = "Actualizar producto")
    @ApiResponse(responseCode = "404", description = "No existe o fue dado de baja")
    @ApiResponse(responseCode = "409", description = "SKU duplicado")
    public ProductoResponseDTO actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequestDTO request) {
        return productoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Permisos.ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Dar de baja un producto (borrado lógico)")
    public void eliminar(@PathVariable Long id) {
        productoService.eliminar(id);
    }
}
