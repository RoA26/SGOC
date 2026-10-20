package com.unisen.sgp.controller;

import com.unisen.sgp.model.dto.VentaDTO.AnulacionRequest;
import com.unisen.sgp.model.dto.VentaDTO.Cotizacion;
import com.unisen.sgp.model.dto.VentaDTO.CotizacionRequest;
import com.unisen.sgp.model.dto.VentaDTO.Detalle;
import com.unisen.sgp.model.dto.VentaDTO.DevolucionRequest;
import com.unisen.sgp.model.dto.VentaDTO.ProductoVenta;
import com.unisen.sgp.model.dto.VentaDTO.Resumen;
import com.unisen.sgp.model.dto.VentaDTO.VentaRequest;
import com.unisen.sgp.model.entity.EstadoVenta;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.service.ResultadoIdempotente;
import com.unisen.sgp.service.comercial.DevolucionVentaService;
import com.unisen.sgp.service.comercial.VentaService;
import com.unisen.sgp.service.documento.ComprobanteVentaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Punto de venta: catálogo de la caja, cotización, ventas, historial, comprobantes, devoluciones
 * y anulaciones. La empresa sale siempre del contexto de seguridad, nunca de la petición.
 */
@RestController
@RequestMapping("/api/v1/ventas")
@Tag(name = "Ventas (POS)")
public class VentaController {

    private final VentaService ventaService;
    private final DevolucionVentaService devolucionService;
    private final ComprobanteVentaService comprobanteService;

    public VentaController(VentaService ventaService, DevolucionVentaService devolucionService,
                           ComprobanteVentaService comprobanteService) {
        this.ventaService = ventaService;
        this.devolucionService = devolucionService;
        this.comprobanteService = comprobanteService;
    }

    @GetMapping("/productos")
    @PreAuthorize(Permisos.LECTURA_OPERATIVA)
    @Operation(summary = "Catálogo de la caja: productos a la venta con precio vigente y existencias (sin costos)")
    public Page<ProductoVenta> catalogo(
            @Parameter(description = "Texto en nombre, SKU o código de barras") @RequestParam(required = false) String q,
            @Parameter(description = "Solo con unidades disponibles") @RequestParam(defaultValue = "false") boolean disponibles,
            @ParameterObject @PageableDefault(size = 24, sort = {"producto.nombre", "id"}) Pageable pageable) {
        return ventaService.catalogo(q, disponibles, pageable);
    }

    @GetMapping("/productos/codigo")
    @PreAuthorize(Permisos.LECTURA_OPERATIVA)
    @Operation(summary = "Escáner: producto a la venta por código de barras exacto o SKU")
    @ApiResponse(responseCode = "404", description = "Ningún producto de la empresa tiene ese código")
    @ApiResponse(responseCode = "409", description = "Existe pero no está a la venta")
    public ProductoVenta porCodigo(@RequestParam String codigo) {
        return ventaService.porCodigo(codigo);
    }

    @PostMapping("/cotizacion")
    @PreAuthorize(Permisos.MIEMBRO_EMPRESA)
    @Operation(summary = "Calcular el carrito (base, descuentos, IVA, total y validaciones) sin registrar nada")
    public Cotizacion cotizar(@Valid @RequestBody CotizacionRequest request) {
        return ventaService.cotizar(request);
    }

    @PostMapping
    @PreAuthorize(Permisos.MIEMBRO_EMPRESA)
    @Operation(summary = "Confirmar una venta (ADMINISTRADOR_COMERCIAL o TRABAJADOR)",
            description = "Transacción única: precios e IVA del catálogo, descuentos validados contra la política, "
                    + "existencias bloqueadas y descontadas con su movimiento, pagos que suman el total. Idempotente por "
                    + "claveIdempotencia.")
    @ApiResponse(responseCode = "201", description = "Registrada")
    @ApiResponse(responseCode = "200", description = "Ya estaba registrada con esa clave (no se duplica)")
    @ApiResponse(responseCode = "400", description = "Líneas, descuentos o pagos inválidos")
    @ApiResponse(responseCode = "403", description = "Descuento o venta bajo el costo sin autorización válida")
    @ApiResponse(responseCode = "409", description = "Existencias insuficientes (detalle en faltantes) o clave usada con otros datos")
    public ResponseEntity<Detalle> confirmar(@Valid @RequestBody VentaRequest request) {
        return respuesta(ventaService.confirmar(request));
    }

    @GetMapping
    @PreAuthorize(Permisos.LECTURA_OPERATIVA)
    @Operation(summary = "Historial de ventas (paginado)", description = "El trabajador ve solo las suyas.")
    public Page<Resumen> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) EstadoVenta estado,
            @Parameter(description = "Solo gestión") @RequestParam(required = false) Long vendedorId,
            @RequestParam(required = false) Long numero,
            @ParameterObject @PageableDefault(size = 20, sort = {"vendidaEn", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ventaService.listar(desde, hasta, estado, vendedorId, numero, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.LECTURA_OPERATIVA)
    @Operation(summary = "Detalle de una venta (costos y rentabilidad solo para la gestión)")
    public Detalle obtener(@PathVariable Long id) {
        return ventaService.obtener(id);
    }

    @GetMapping("/{id}/comprobante")
    @PreAuthorize(Permisos.LECTURA_OPERATIVA)
    @Operation(summary = "Comprobante interno de venta en PDF (no es factura electrónica)")
    @ApiResponse(responseCode = "200", description = "PDF", content = @Content(mediaType = "application/pdf"))
    public ResponseEntity<byte[]> comprobante(@PathVariable Long id) {
        ComprobanteVentaService.Archivo archivo = comprobanteService.comprobante(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(archivo.nombre(), StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(archivo.contenido());
    }

    @PostMapping("/{id}/devoluciones")
    @PreAuthorize(Permisos.ADMINISTRADOR_COMERCIAL)
    @Operation(summary = "Registrar una devolución parcial o total (ADMINISTRADOR_COMERCIAL)",
            description = "Sin superar lo que queda por devolver; cada línea indica si vuelve al inventario. Idempotente.")
    public ResponseEntity<Detalle> devolver(@PathVariable Long id, @Valid @RequestBody DevolucionRequest request) {
        return respuesta(devolucionService.devolver(id, request));
    }

    @PostMapping("/{id}/anulacion")
    @PreAuthorize(Permisos.ADMINISTRADOR_COMERCIAL)
    @Operation(summary = "Anular una venta del mismo día sin devoluciones (ADMINISTRADOR_COMERCIAL)")
    public ResponseEntity<Detalle> anular(@PathVariable Long id, @Valid @RequestBody AnulacionRequest request) {
        return respuesta(devolucionService.anular(id, request));
    }

    private static ResponseEntity<Detalle> respuesta(ResultadoIdempotente<Detalle> resultado) {
        return ResponseEntity.status(resultado.nuevo() ? HttpStatus.CREATED : HttpStatus.OK).body(resultado.valor());
    }
}
