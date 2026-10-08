package com.unisen.sgp.controller;

import com.unisen.sgp.model.dto.EmpresaRequestDTO;
import com.unisen.sgp.model.dto.EmpresaResponseDTO;
import com.unisen.sgp.service.EmpresaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Empresas cliente (tenants). La autorización por rol vive en {@link EmpresaService}. */
@RestController
@RequestMapping("/api/v1/empresas")
@Tag(name = "Empresas")
public class EmpresaController {

    private final EmpresaService empresaService;

    public EmpresaController(EmpresaService empresaService) {
        this.empresaService = empresaService;
    }

    @PostMapping
    @Operation(summary = "Aprovisionar una empresa con su gerente fundador (SUPER_ADMIN)",
            description = "Crea la empresa, su código permanente (codigoEmpresa) y su primer GERENTE, ACTIVO, en una "
                    + "sola transacción: si algo falla no se crea nada.")
    @ApiResponse(responseCode = "201", description = "Empresa y gerente creados")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (errors.nit, errors['gerente.username']...)")
    @ApiResponse(responseCode = "403", description = "El usuario no es SUPER_ADMIN")
    @ApiResponse(responseCode = "409", description = "NIT ya registrado, o username/correo del gerente en uso")
    public ResponseEntity<EmpresaResponseDTO> crear(@Valid @RequestBody EmpresaRequestDTO request) {
        EmpresaResponseDTO creada = empresaService.crearConGerente(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creada.id()).toUri();
        return ResponseEntity.created(location).body(creada);
    }

    @GetMapping
    @Operation(summary = "Listar empresas (SUPER_ADMIN, paginado)")
    @ApiResponse(responseCode = "403", description = "El usuario no es SUPER_ADMIN")
    public Page<EmpresaResponseDTO> listar(@ParameterObject @PageableDefault(size = 20, sort = "nombre") Pageable pageable) {
        return empresaService.listar(pageable);
    }

    @GetMapping("/actual")
    @Operation(summary = "Empresa en la que se trabaja (GERENTE, o SUPER_ADMIN con X-Tenant-ID)",
            description = "Incluye el código de empresa que el gerente comparte con sus trabajadores para que se registren.")
    @ApiResponse(responseCode = "400", description = "SUPER_ADMIN sin X-Tenant-ID (modo global)")
    @ApiResponse(responseCode = "403", description = "El usuario no es gestor")
    public EmpresaResponseDTO actual() {
        return empresaService.actual();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una empresa (SUPER_ADMIN)")
    @ApiResponse(responseCode = "403", description = "El usuario no es SUPER_ADMIN")
    @ApiResponse(responseCode = "404", description = "No existe")
    public EmpresaResponseDTO obtener(@PathVariable Long id) {
        return empresaService.obtener(id);
    }
}
