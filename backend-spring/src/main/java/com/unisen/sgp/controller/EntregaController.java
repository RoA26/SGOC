package com.unisen.sgp.controller;

import com.unisen.sgp.model.dto.EntregaDTO;
import com.unisen.sgp.model.dto.EntregaDetalleDTO;
import com.unisen.sgp.model.dto.ResumenEntregasDTO;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.service.EntregaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/entregas")
@Tag(name = "Entregas")
public class EntregaController {

    private final EntregaService entregaService;

    public EntregaController(EntregaService entregaService) {
        this.entregaService = entregaService;
    }

    @GetMapping
    @PreAuthorize(Permisos.LECTURA_OPERATIVA)
    @Operation(summary = "Entregas de las órdenes emitidas (sin costos)",
            description = "Ordenadas por fecha estimada (las sin programar, al final). El trabajador solo consulta "
                    + "las vistas de lo pendiente (PENDIENTES, SIN_PROGRAMAR, PROGRAMADAS, RETRASADAS, PARCIALES).")
    @ApiResponse(responseCode = "403", description = "Vista de supervisión pedida por un trabajador")
    public Page<EntregaDTO> listar(
            @Parameter(description = "Por defecto, PENDIENTES") @RequestParam(required = false) EntregaService.Vista vista,
            @Parameter(description = "Fecha estimada desde (incluida)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Fecha estimada hasta (incluida)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long proveedorId,
            @Parameter(description = "Número de la orden (42 = OC-00042)") @RequestParam(required = false) Long numero,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return entregaService.listar(new EntregaService.Filtro(vista, desde, hasta, proveedorId, numero), pageable);
    }

    @GetMapping("/resumen")
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Cifras de las entregas abiertas (supervisión)")
    public ResumenEntregasDTO resumen() {
        return entregaService.resumen();
    }

    @GetMapping("/{ordenId}")
    @PreAuthorize(Permisos.LECTURA_OPERATIVA)
    @Operation(summary = "Detalle de la entrega de una orden: líneas sin costos, recepciones e historial de fechas")
    @ApiResponse(responseCode = "404", description = "No existe en la empresa (o, para el trabajador, no está emitida)")
    public EntregaDetalleDTO detalle(@PathVariable Long ordenId) {
        return entregaService.detalle(ordenId);
    }
}
