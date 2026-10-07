package com.unisen.sgp.controller;

import com.unisen.sgp.model.dto.CambioEstadoSolicitudDTO;
import com.unisen.sgp.model.dto.SolicitudRequestDTO;
import com.unisen.sgp.model.dto.SolicitudResponseDTO;
import com.unisen.sgp.model.entity.EstadoSolicitud;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.service.SolicitudService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/solicitudes")
@Tag(name = "Solicitudes internas")
public class SolicitudController {

    private final SolicitudService solicitudService;

    public SolicitudController(SolicitudService solicitudService) {
        this.solicitudService = solicitudService;
    }

    @GetMapping
    @Operation(summary = "Listar solicitudes (paginado)",
            description = "USUARIO recibe solo las suyas; ADMIN y GERENTE, todas. Por defecto, las más recientes primero.")
    public Page<SolicitudResponseDTO> listar(
            @Parameter(description = "Filtra por estado") @RequestParam(required = false) EstadoSolicitud estado,
            @ParameterObject @PageableDefault(size = 10, sort = {"fecha", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return solicitudService.listar(estado, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una solicitud con su detalle")
    @ApiResponse(responseCode = "404", description = "No existe o pertenece a otro usuario")
    public SolicitudResponseDTO obtener(@PathVariable Long id) {
        return solicitudService.obtener(id);
    }

    @PostMapping
    @Operation(summary = "Crear solicitud",
            description = "El solicitante es el usuario autenticado y el estado inicial, PENDIENTE.")
    @ApiResponse(responseCode = "201", description = "Creada con todas sus líneas")
    @ApiResponse(responseCode = "400",
            description = "Datos inválidos; errors.detalles[i].productoId si un producto no existe o se repite")
    public ResponseEntity<SolicitudResponseDTO> crear(@Valid @RequestBody SolicitudRequestDTO request) {
        SolicitudResponseDTO creada = solicitudService.crear(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creada.id()).toUri();
        return ResponseEntity.created(location).body(creada);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Aprobar o rechazar una solicitud (ADMIN / GERENTE)",
            description = "Solo desde PENDIENTE. El comentario es obligatorio al rechazar.")
    @ApiResponse(responseCode = "200", description = "Solicitud revisada")
    @ApiResponse(responseCode = "400", description = "Estado no válido o falta el motivo del rechazo")
    @ApiResponse(responseCode = "403", description = "El usuario no es ADMIN ni GERENTE")
    @ApiResponse(responseCode = "404", description = "No existe")
    @ApiResponse(responseCode = "409", description = "Ya estaba aprobada o rechazada")
    public SolicitudResponseDTO cambiarEstado(@PathVariable Long id,
                                              @Valid @RequestBody CambioEstadoSolicitudDTO request) {
        return solicitudService.cambiarEstado(id, request);
    }
}
