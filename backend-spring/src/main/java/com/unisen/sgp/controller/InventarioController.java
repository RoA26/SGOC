package com.unisen.sgp.controller;

import com.unisen.sgp.model.dto.AjusteInventarioDTO;
import com.unisen.sgp.model.dto.ExistenciaDTO;
import com.unisen.sgp.model.dto.MovimientoInventarioDTO;
import com.unisen.sgp.model.entity.EstadoStock;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.service.InventarioService;
import com.unisen.sgp.service.ResultadoIdempotente;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventario")
@Tag(name = "Inventario")
public class InventarioController {

    private final InventarioService inventarioService;

    public InventarioController(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    @GetMapping
    @Operation(summary = "Existencias de los productos activos (paginado)",
            description = "Para toda persona de la empresa; sin costos. Orden por defecto: nombre del producto.")
    public Page<ExistenciaDTO> listar(
            @Parameter(description = "AGOTADO, CRITICO o NORMAL") @RequestParam(required = false) EstadoStock estado,
            @Parameter(description = "Texto en nombre, SKU o código de barras") @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20, sort = {"producto.nombre", "id"}) Pageable pageable) {
        return inventarioService.listar(estado, StringUtils.hasText(q) ? q.strip() : null, pageable);
    }

    @GetMapping("/movimientos")
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Kárdex: movimientos de inventario (paginado)", description = "Los más recientes primero.")
    public Page<MovimientoInventarioDTO> movimientos(
            @Parameter(description = "Solo los de un producto") @RequestParam(required = false) Long productoId,
            @ParameterObject @PageableDefault(size = 20, sort = {"fecha", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return inventarioService.movimientos(productoId, pageable);
    }

    @PostMapping("/ajustes")
    @PreAuthorize(Permisos.ADMIN_EMPRESA)
    @Operation(summary = "Registrar un ajuste de inventario (ADMIN_EMPRESA)",
            description = "Cantidad con signo y motivo obligatorio. Idempotente por claveIdempotencia.")
    @ApiResponse(responseCode = "201", description = "Registrado")
    @ApiResponse(responseCode = "200", description = "Ya estaba registrado con esa clave (no se duplica)")
    @ApiResponse(responseCode = "409", description = "Existencias insuficientes o clave usada con otros datos")
    public ResponseEntity<MovimientoInventarioDTO> ajustar(@Valid @RequestBody AjusteInventarioDTO request) {
        ResultadoIdempotente<MovimientoInventarioDTO> resultado = inventarioService.ajustar(request);
        return ResponseEntity.status(resultado.nuevo() ? HttpStatus.CREATED : HttpStatus.OK).body(resultado.valor());
    }
}
