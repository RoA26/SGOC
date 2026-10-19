package com.unisen.sgp.controller;

import com.unisen.sgp.model.dto.CalendarioDTO;
import com.unisen.sgp.model.dto.EventoCalendarioDTO;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.service.CalendarioOperativoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calendario")
@Tag(name = "Calendario operativo")
public class CalendarioController {

    private final CalendarioOperativoService calendarioService;

    public CalendarioController(CalendarioOperativoService calendarioService) {
        this.calendarioService = calendarioService;
    }

    @GetMapping
    @PreAuthorize(Permisos.LECTURA_OPERATIVA)
    @Operation(summary = "Eventos del calendario de la empresa",
            description = "Órdenes emitidas (gestión), entregas en su fecha estimada vigente, recepciones reales y "
                    + "solicitudes pendientes de aprobación (el trabajador, solo las suyas). Máximo 62 días. Las "
                    + "entregas sin franja son de todo el día.")
    @ApiResponse(responseCode = "400", description = "Período inválido o SUPER_ADMIN en modo global")
    public CalendarioDTO eventos(
            @Parameter(description = "Primer día (incluido)", required = true) @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Último día (incluido)", required = true) @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @Parameter(description = "Tipos de evento (por defecto, todos los del rol)") @RequestParam(required = false)
            Set<EventoCalendarioDTO.Tipo> tipos) {
        return calendarioService.eventos(desde, hasta, tipos);
    }
}
