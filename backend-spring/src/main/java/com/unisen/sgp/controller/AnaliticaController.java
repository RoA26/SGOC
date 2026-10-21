package com.unisen.sgp.controller;

import com.unisen.sgp.model.dto.AnaliticaDTO;
import com.unisen.sgp.model.dto.AnaliticaDTO.AlcanceEmpresas;
import com.unisen.sgp.model.dto.AnaliticaDTO.OrdenesDeEmpresa;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.service.AnaliticaService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analitica")
@Tag(name = "Analítica")
public class AnaliticaController {

    private static final String PERIODO = "Período en fechas locales (zona del negocio), ambas incluidas; "
            + "por defecto los últimos 30 días. Máximo 366 días y nunca futuro.";
    private static final String ALCANCE = "REALES (por defecto): solo las empresas reales; DEMO: solo las de "
            + "demostración; TODAS: ambas (cada empresa indica si es demo).";

    private final AnaliticaService analiticaService;

    public AnaliticaController(AnaliticaService analiticaService) {
        this.analiticaService = analiticaService;
    }

    @GetMapping("/global")
    @PreAuthorize(Permisos.SUPER_ADMIN)
    @Operation(summary = "Indicadores de la plataforma (SUPER_ADMIN, con X-Tenant-ID: global)", description = PERIODO)
    @ApiResponse(responseCode = "400", description = "Período no válido o petición dentro de una empresa")
    @ApiResponse(responseCode = "403", description = "No es SUPER_ADMIN")
    public AnaliticaDTO.Global global(
            @Parameter(example = "2026-09-17") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(example = "2026-10-16") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @Parameter(description = ALCANCE) @RequestParam(defaultValue = "REALES") AlcanceEmpresas alcance) {
        return analiticaService.global(desde, hasta, alcance);
    }

    @GetMapping("/global/empresas")
    @PreAuthorize(Permisos.SUPER_ADMIN)
    @Operation(summary = "Órdenes por empresa (SUPER_ADMIN, con X-Tenant-ID: global)",
            description = "Las empresas del alcance, las de más órdenes emitidas en el período primero. " + PERIODO)
    public Page<OrdenesDeEmpresa> ordenesPorEmpresa(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @Parameter(description = ALCANCE) @RequestParam(defaultValue = "REALES") AlcanceEmpresas alcance,
            @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return analiticaService.ordenesPorEmpresa(desde, hasta, alcance, pageable);
    }

    @GetMapping("/empresa")
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Indicadores de la empresa (ADMINISTRADOR_COMERCIAL, o SUPER_ADMIN con X-Tenant-ID: <id>)",
            description = PERIODO)
    @ApiResponse(responseCode = "400", description = "Período no válido o SUPER_ADMIN sin empresa")
    public AnaliticaDTO.DeEmpresa empresa(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return analiticaService.deEmpresa(desde, hasta);
    }

    @GetMapping("/personal")
    @PreAuthorize(Permisos.MIEMBRO_EMPRESA)
    @Operation(summary = "Indicadores personales (TRABAJADOR o ADMINISTRADOR_COMERCIAL)",
            description = "Productos disponibles y críticos de su empresa y sus propias solicitudes. Sin ventas.")
    public AnaliticaDTO.Personal personal() {
        return analiticaService.personal();
    }
}
