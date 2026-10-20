package com.unisen.sgp.controller;

import com.unisen.sgp.model.dto.ReporteVentasDTO;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.service.comercial.ReporteVentasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reportes comerciales calculados con los datos históricos de ventas y devoluciones. */
@RestController
@RequestMapping("/api/v1/reportes")
@Tag(name = "Reportes comerciales")
public class ReporteController {

    private final ReporteVentasService reporteService;

    public ReporteController(ReporteVentasService reporteService) {
        this.reporteService = reporteService;
    }

    @GetMapping("/ventas")
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Reporte de ventas y rentabilidad bruta del período (gestión)",
            description = "Sin fechas: los últimos 30 días. Máximo 366 días; nunca futuro. Días en la zona del negocio.")
    public ReporteVentasDTO.Reporte ventas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reporteService.reporte(desde, hasta);
    }

    @GetMapping("/ventas/mias")
    @PreAuthorize(Permisos.MIEMBRO_EMPRESA)
    @Operation(summary = "Mis ventas de un día (hoy por defecto), sin costos")
    public ReporteVentasDTO.MisVentas misVentas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia) {
        return reporteService.misVentas(dia);
    }
}
