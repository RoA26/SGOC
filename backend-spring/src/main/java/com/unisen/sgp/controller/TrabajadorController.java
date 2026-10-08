package com.unisen.sgp.controller;

import com.unisen.sgp.model.dto.CambioEstadoUsuarioDTO;
import com.unisen.sgp.model.dto.TrabajadorResponseDTO;
import com.unisen.sgp.model.entity.EstadoUsuario;
import com.unisen.sgp.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trabajadores (rol USUARIO) de la empresa en la que se trabaja: el GERENTE revisa los que se
 * registraron con el código de empresa y los aprueba, rechaza, desactiva o reactiva. La
 * autorización (rol y empresa) vive en {@link UsuarioService}.
 */
@RestController
@RequestMapping("/api/v1/trabajadores")
@Tag(name = "Trabajadores")
public class TrabajadorController {

    private final UsuarioService usuarioService;

    public TrabajadorController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    @Operation(summary = "Listar trabajadores de la empresa (GERENTE, o SUPER_ADMIN con X-Tenant-ID)",
            description = "Solo los de la empresa de la petición. ?estado=PENDIENTE devuelve los que esperan aprobación.")
    @ApiResponse(responseCode = "400", description = "SUPER_ADMIN sin X-Tenant-ID (modo global)")
    @ApiResponse(responseCode = "403", description = "El usuario no es gestor")
    public Page<TrabajadorResponseDTO> listar(
            @Parameter(description = "Filtra por estado") @RequestParam(required = false) EstadoUsuario estado,
            @ParameterObject @PageableDefault(size = 20, sort = {"creadoEn", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return usuarioService.listarTrabajadores(estado, pageable);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Aprobar, rechazar, desactivar o reactivar un trabajador",
            description = "PENDIENTE → ACTIVO | RECHAZADO; ACTIVO → INACTIVO; INACTIVO | RECHAZADO → ACTIVO. "
                    + "Rige desde la siguiente petición del trabajador, aunque su JWT siga vigente.")
    @ApiResponse(responseCode = "200", description = "Estado actualizado")
    @ApiResponse(responseCode = "400", description = "Estado no válido (p. ej. PENDIENTE)")
    @ApiResponse(responseCode = "403", description = "El usuario no es gestor")
    @ApiResponse(responseCode = "404", description = "No es un trabajador de esta empresa")
    @ApiResponse(responseCode = "409", description = "Ya estaba en ese estado o la transición no existe")
    public TrabajadorResponseDTO cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoUsuarioDTO request) {
        return usuarioService.cambiarEstadoTrabajador(id, request);
    }
}
