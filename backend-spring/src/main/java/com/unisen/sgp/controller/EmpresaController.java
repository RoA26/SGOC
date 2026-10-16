package com.unisen.sgp.controller;

import com.unisen.sgp.model.dto.CambioEstadoEmpresaDTO;
import com.unisen.sgp.model.dto.CambioEstadoUsuarioDTO;
import com.unisen.sgp.model.dto.CodigoEmpresarialDTO;
import com.unisen.sgp.model.dto.EmpresaRequestDTO;
import com.unisen.sgp.model.dto.EmpresaResponseDTO;
import com.unisen.sgp.model.dto.TrabajadorResponseDTO;
import com.unisen.sgp.service.EmpresaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    @Operation(summary = "Crear una empresa con su administrador principal (SUPER_ADMIN)",
            description = "Crea la empresa, su código empresarial (el indicado o el siguiente libre), su código de registro "
                    + "de trabajadores (codigoEmpresa) y su administrador inicial (ADMIN_EMPRESA, ACTIVO) en una sola transacción: si algo "
                    + "falla no se crea nada.")
    @ApiResponse(responseCode = "201", description = "Empresa y administrador creados")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (errors.codigo, errors.nit, errors['administrador.nombres']...)")
    @ApiResponse(responseCode = "403", description = "El usuario no es SUPER_ADMIN")
    @ApiResponse(responseCode = "409", description = "NIT o código ya registrados, o username/correo del administrador en uso")
    public ResponseEntity<EmpresaResponseDTO> crear(@Valid @RequestBody EmpresaRequestDTO request) {
        EmpresaResponseDTO creada = empresaService.crearConAdministrador(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creada.id()).toUri();
        return ResponseEntity.created(location).body(creada);
    }

    @GetMapping
    @Operation(summary = "Listar empresas con su administrador principal (SUPER_ADMIN, paginado)")
    @ApiResponse(responseCode = "403", description = "El usuario no es SUPER_ADMIN")
    public Page<EmpresaResponseDTO> listar(@ParameterObject @PageableDefault(size = 20, sort = "nombre") Pageable pageable) {
        return empresaService.listar(pageable);
    }

    @GetMapping("/codigo-sugerido")
    @Operation(summary = "Proponer el código empresarial de una empresa nueva (SUPER_ADMIN)",
            description = "Prefijo del nombre y primer número libre en la BD: NASCAR → NASC01. Orientativo: el alta lo "
                    + "vuelve a comprobar.")
    @ApiResponse(responseCode = "400", description = "Falta el nombre (errors.nombre)")
    @ApiResponse(responseCode = "403", description = "El usuario no es SUPER_ADMIN")
    public CodigoEmpresarialDTO sugerirCodigo(
            @Parameter(description = "Nombre comercial", example = "NASCAR") @RequestParam(required = false) String nombre) {
        return empresaService.sugerirCodigo(nombre);
    }

    @GetMapping("/codigo-disponible")
    @Operation(summary = "Comprobar si un código empresarial está libre (SUPER_ADMIN)")
    @ApiResponse(responseCode = "400", description = "Formato no válido (errors.codigo)")
    @ApiResponse(responseCode = "403", description = "El usuario no es SUPER_ADMIN")
    public CodigoEmpresarialDTO comprobarCodigo(
            @Parameter(description = "Código a comprobar", example = "NASC01") @RequestParam(required = false) String codigo) {
        return empresaService.comprobarCodigo(codigo);
    }

    @GetMapping("/actual")
    @Operation(summary = "Empresa en la que se trabaja (ADMIN_EMPRESA, o SUPER_ADMIN con X-Tenant-ID)",
            description = "Incluye el código de registro que el administrador comparte con sus trabajadores.")
    @ApiResponse(responseCode = "400", description = "SUPER_ADMIN sin X-Tenant-ID (modo global)")
    @ApiResponse(responseCode = "403", description = "El usuario no es gestor")
    public EmpresaResponseDTO actual() {
        return empresaService.actual();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ficha de una empresa con su administrador principal (SUPER_ADMIN)")
    @ApiResponse(responseCode = "403", description = "El usuario no es SUPER_ADMIN")
    @ApiResponse(responseCode = "404", description = "No existe")
    public EmpresaResponseDTO obtener(@PathVariable Long id) {
        return empresaService.obtener(id);
    }

    @GetMapping("/{id}/usuarios")
    @Operation(summary = "Usuarios de una empresa y su estado (SUPER_ADMIN, solo lectura)",
            description = "Administradores y trabajadores de la empresa indicada, nunca de otra.")
    @ApiResponse(responseCode = "403", description = "El usuario no es SUPER_ADMIN")
    @ApiResponse(responseCode = "404", description = "La empresa no existe")
    public Page<TrabajadorResponseDTO> listarUsuarios(
            @PathVariable Long id,
            @ParameterObject @PageableDefault(size = 20, sort = {"rol", "nombre"}) Pageable pageable) {
        return empresaService.listarUsuarios(id, pageable);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Activar o desactivar una empresa (SUPER_ADMIN)",
            description = "Desactivada, ninguno de sus usuarios puede operar desde su siguiente petición (403, motivo "
                    + "EMPRESA_INACTIVA). Sus datos se conservan.")
    @ApiResponse(responseCode = "200", description = "Estado actualizado")
    @ApiResponse(responseCode = "400", description = "Falta activa")
    @ApiResponse(responseCode = "403", description = "El usuario no es SUPER_ADMIN")
    @ApiResponse(responseCode = "404", description = "La empresa no existe")
    @ApiResponse(responseCode = "409", description = "Ya estaba en ese estado")
    public EmpresaResponseDTO cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoEmpresaDTO request) {
        return empresaService.cambiarEstado(id, request);
    }

    @PatchMapping("/{id}/administradores/{usuarioId}/estado")
    @Operation(summary = "Desactivar o reactivar la cuenta de un administrador de la empresa (SUPER_ADMIN)",
            description = "ACTIVO → INACTIVO o INACTIVO → ACTIVO. Rige desde la siguiente petición del administrador, aunque su "
                    + "JWT siga vigente.")
    @ApiResponse(responseCode = "200", description = "Estado actualizado")
    @ApiResponse(responseCode = "400", description = "Estado no válido")
    @ApiResponse(responseCode = "403", description = "El usuario no es SUPER_ADMIN")
    @ApiResponse(responseCode = "404", description = "No es un administrador de esa empresa")
    @ApiResponse(responseCode = "409", description = "Ya estaba en ese estado o la transición no existe")
    public TrabajadorResponseDTO cambiarEstadoAdministrador(@PathVariable Long id, @PathVariable Long usuarioId,
                                                      @Valid @RequestBody CambioEstadoUsuarioDTO request) {
        return empresaService.cambiarEstadoAdministrador(id, usuarioId, request);
    }
}
