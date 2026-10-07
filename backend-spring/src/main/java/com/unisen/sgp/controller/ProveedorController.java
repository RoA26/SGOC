package com.unisen.sgp.controller;

import com.unisen.sgp.model.dto.ProveedorRequestDTO;
import com.unisen.sgp.model.dto.ProveedorResponseDTO;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.service.ProveedorService;
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
@RequestMapping("/api/v1/proveedores")
@Tag(name = "Proveedores")
public class ProveedorController {

    private final ProveedorService proveedorService;

    public ProveedorController(ProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    @GetMapping
    @Operation(summary = "Listar proveedores activos (paginado)",
            description = "Parámetros: page (desde 0), size (máx. 100), sort=campo,asc|desc.")
    public Page<ProveedorResponseDTO> listar(
            @ParameterObject @PageableDefault(size = 10, sort = {"razonSocial", "id"}) Pageable pageable) {
        return proveedorService.listar(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un proveedor")
    @ApiResponse(responseCode = "404", description = "No existe o fue dado de baja")
    public ProveedorResponseDTO obtener(@PathVariable Long id) {
        return proveedorService.obtener(id);
    }

    @PostMapping
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Crear proveedor")
    @ApiResponse(responseCode = "201", description = "Creado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    @ApiResponse(responseCode = "409", description = "NIT duplicado")
    public ResponseEntity<ProveedorResponseDTO> crear(@Valid @RequestBody ProveedorRequestDTO request) {
        ProveedorResponseDTO creado = proveedorService.crear(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Actualizar proveedor")
    @ApiResponse(responseCode = "404", description = "No existe o fue dado de baja")
    @ApiResponse(responseCode = "409", description = "NIT duplicado")
    public ProveedorResponseDTO actualizar(@PathVariable Long id, @Valid @RequestBody ProveedorRequestDTO request) {
        return proveedorService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Permisos.GESTION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Dar de baja un proveedor (borrado lógico)")
    @ApiResponse(responseCode = "409", description = "Tiene productos activos")
    public void eliminar(@PathVariable Long id) {
        proveedorService.eliminar(id);
    }
}
