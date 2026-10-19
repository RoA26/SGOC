package com.unisen.sgp.controller;

import com.unisen.sgp.model.dto.RecepcionResponseDTO;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.service.RecepcionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recepciones")
@Tag(name = "Recepciones")
public class RecepcionController {

    private final RecepcionService recepcionService;

    public RecepcionController(RecepcionService recepcionService) {
        this.recepcionService = recepcionService;
    }

    @GetMapping
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Listar recepciones (paginado, supervisión)",
            description = "Por defecto, las más recientes primero. Fechas en días de la zona del negocio.")
    public Page<RecepcionResponseDTO> listar(
            @Parameter(description = "Recibidas desde este día (incluido)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Recibidas hasta este día (incluido)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long proveedorId,
            @RequestParam(required = false) Long ordenId,
            @Parameter(description = "Usuario que la registró") @RequestParam(required = false) Long registradaPorId,
            @Parameter(description = "true: solo con incidencias; false: solo sin ellas") @RequestParam(required = false)
            Boolean conIncidencias,
            @ParameterObject @PageableDefault(size = 10, sort = {"recibidaEn", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return recepcionService.listar(new RecepcionService.Filtro(desde, hasta, proveedorId, ordenId, registradaPorId,
                conIncidencias), pageable);
    }

    @GetMapping("/mias")
    @PreAuthorize(Permisos.MIEMBRO_EMPRESA)
    @Operation(summary = "Mis recepciones (historial personal)",
            description = "Las que registró quien pregunta (según su token). Sin costos para el trabajador.")
    public Page<RecepcionResponseDTO> mias(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long ordenId,
            @RequestParam(required = false) Boolean conIncidencias,
            @ParameterObject @PageableDefault(size = 10, sort = {"recibidaEn", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return recepcionService.mias(new RecepcionService.Filtro(desde, hasta, null, ordenId, null, conIncidencias),
                pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.LECTURA_OPERATIVA)
    @Operation(summary = "Detalle de una recepción, con sus líneas e incidencias")
    @ApiResponse(responseCode = "404", description = "No existe en la empresa")
    public RecepcionResponseDTO obtener(@PathVariable Long id) {
        return recepcionService.obtener(id);
    }
}
