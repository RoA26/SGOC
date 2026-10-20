package com.unisen.sgp.controller;

import com.unisen.sgp.model.dto.ComercialDTO.AutorizacionDTO;
import com.unisen.sgp.model.dto.ComercialDTO.AutorizacionRequest;
import com.unisen.sgp.model.dto.ComercialDTO.CambioPrecioDTO;
import com.unisen.sgp.model.dto.ComercialDTO.CondicionesVentaRequest;
import com.unisen.sgp.model.dto.ComercialDTO.PoliticaDTO;
import com.unisen.sgp.model.dto.ComercialDTO.PoliticaRequest;
import com.unisen.sgp.model.dto.ComercialDTO.ProductoComercial;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.service.comercial.ComercialService;
import com.unisen.sgp.service.comercial.ComercialService.FiltroComercial;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestión comercial: precios de venta, IVA, rentabilidad, historial, política y autorizaciones.
 * Consulta: gestión (administrador comercial; SUPER_ADMIN supervisando). Cambios: solo el
 * administrador comercial de la empresa.
 */
@RestController
@RequestMapping("/api/v1/comercial")
@Tag(name = "Gestión comercial")
public class ComercialController {

    private final ComercialService comercialService;

    public ComercialController(ComercialService comercialService) {
        this.comercialService = comercialService;
    }

    @GetMapping("/productos")
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Productos con precio de venta, IVA, costo promedio y rentabilidad unitaria (paginado)")
    public Page<ProductoComercial> productos(
            @Parameter(description = "Texto en nombre, SKU o código de barras") @RequestParam(required = false) String q,
            @Parameter(description = "TODOS, A_LA_VENTA, SIN_PRECIO, SIN_COSTO o BAJO_COSTO")
            @RequestParam(required = false) FiltroComercial filtro,
            @ParameterObject @PageableDefault(size = 20, sort = {"producto.nombre", "id"}) Pageable pageable) {
        return comercialService.productos(q, filtro, pageable);
    }

    @GetMapping("/productos/{id}")
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Condiciones de venta y rentabilidad de un producto")
    public ProductoComercial producto(@PathVariable Long id) {
        return comercialService.producto(id);
    }

    @PutMapping("/productos/{id}/condiciones")
    @PreAuthorize(Permisos.ADMINISTRADOR_COMERCIAL)
    @Operation(summary = "Cambiar el precio de venta (sin IVA) y el tratamiento del IVA (ADMINISTRADOR_COMERCIAL)",
            description = "Registra el cambio en el historial con el valor anterior, quién y cuándo. Un precio bajo el "
                    + "costo se permite y se advierte (bajoCosto). Sin cambios, no registra nada.")
    @ApiResponse(responseCode = "200", description = "Condiciones vigentes")
    @ApiResponse(responseCode = "400", description = "Precio, tratamiento o tarifa no válidos")
    @ApiResponse(responseCode = "403", description = "Sin permiso (trabajador o SUPER_ADMIN)")
    @ApiResponse(responseCode = "404", description = "No existe en la empresa")
    public ProductoComercial cambiarCondiciones(@PathVariable Long id, @Valid @RequestBody CondicionesVentaRequest request) {
        return comercialService.cambiarCondiciones(id, request);
    }

    @GetMapping("/historial-precios")
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Historial de cambios de condiciones de venta (paginado, los más recientes primero)")
    public Page<CambioPrecioDTO> historial(
            @Parameter(description = "Solo los de un producto") @RequestParam(required = false) Long productoId,
            @ParameterObject @PageableDefault(size = 20, sort = {"fecha", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return comercialService.historial(productoId, pageable);
    }

    @GetMapping("/politica")
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Política comercial vigente (porDefecto = la empresa aún no la definió)")
    public PoliticaDTO politica() {
        return comercialService.consultarPolitica();
    }

    @PutMapping("/politica")
    @PreAuthorize(Permisos.ADMINISTRADOR_COMERCIAL)
    @Operation(summary = "Definir la política comercial (ADMINISTRADOR_COMERCIAL)")
    public PoliticaDTO actualizarPolitica(@Valid @RequestBody PoliticaRequest request) {
        return comercialService.actualizarPolitica(request);
    }

    @PostMapping("/autorizaciones")
    @PreAuthorize(Permisos.ADMINISTRADOR_COMERCIAL)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Emitir un código de autorización de un solo uso (ADMINISTRADOR_COMERCIAL)",
            description = "Para una venta con descuento por encima del límite de quien vende o bajo el costo. El código "
                    + "completo solo se muestra en esta respuesta.")
    public AutorizacionDTO crearAutorizacion(@Valid @RequestBody AutorizacionRequest request) {
        return comercialService.crearAutorizacion(request);
    }

    @GetMapping("/autorizaciones")
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Autorizaciones emitidas (paginado; sin el código completo)")
    public Page<AutorizacionDTO> autorizaciones(
            @ParameterObject @PageableDefault(size = 20, sort = {"creadaEn", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return comercialService.autorizaciones(pageable);
    }
}
