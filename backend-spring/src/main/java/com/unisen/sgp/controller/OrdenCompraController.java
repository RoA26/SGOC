package com.unisen.sgp.controller;

import com.unisen.sgp.model.dto.CancelacionOrdenDTO;
import com.unisen.sgp.model.dto.DocumentoOrdenDTO;
import com.unisen.sgp.model.dto.EventoHistorialDTO;
import com.unisen.sgp.model.dto.OrdenCompraEdicionDTO;
import com.unisen.sgp.model.dto.OrdenCompraRequestDTO;
import com.unisen.sgp.model.dto.OrdenCompraResponseDTO;
import com.unisen.sgp.model.dto.ProgramacionEntregaDTO;
import com.unisen.sgp.model.dto.ProgramacionEntregaRequestDTO;
import com.unisen.sgp.model.dto.RecepcionRequestDTO;
import com.unisen.sgp.model.dto.RecepcionResponseDTO;
import com.unisen.sgp.model.entity.EstadoOrdenCompra;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.service.EntregaService;
import com.unisen.sgp.service.HistorialOrdenService;
import com.unisen.sgp.service.OrdenCompraService;
import com.unisen.sgp.service.RecepcionService;
import com.unisen.sgp.service.ResultadoIdempotente;
import com.unisen.sgp.service.documento.DocumentoOrdenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/ordenes-compra")
@Tag(name = "Órdenes de compra")
public class OrdenCompraController {

    private final OrdenCompraService ordenService;
    private final RecepcionService recepcionService;
    private final DocumentoOrdenService documentoService;
    private final EntregaService entregaService;
    private final HistorialOrdenService historialService;

    public OrdenCompraController(OrdenCompraService ordenService, RecepcionService recepcionService,
                                 DocumentoOrdenService documentoService, EntregaService entregaService,
                                 HistorialOrdenService historialService) {
        this.ordenService = ordenService;
        this.recepcionService = recepcionService;
        this.documentoService = documentoService;
        this.entregaService = entregaService;
        this.historialService = historialService;
    }

    @GetMapping
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Listar órdenes de compra (paginado)", description = "Por defecto, las más recientes primero.")
    public Page<OrdenCompraResponseDTO> listar(
            @Parameter(description = "Filtra por estado") @RequestParam(required = false) EstadoOrdenCompra estado,
            @Parameter(description = "Filtra por proveedor") @RequestParam(required = false) Long proveedorId,
            @Parameter(description = "Órdenes generadas desde una solicitud") @RequestParam(required = false)
            Long solicitudId,
            @ParameterObject @PageableDefault(size = 10, sort = {"numero"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ordenService.listar(estado, proveedorId, solicitudId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Obtener una orden con sus líneas")
    @ApiResponse(responseCode = "404", description = "No existe en la empresa")
    public OrdenCompraResponseDTO obtener(@PathVariable Long id) {
        return ordenService.obtener(id);
    }

    @PostMapping
    @PreAuthorize(Permisos.ADMINISTRADOR_COMERCIAL)
    @Operation(summary = "Crear una orden en borrador (ADMINISTRADOR_COMERCIAL)",
            description = "Todos los productos deben ser del proveedor elegido. Sin costo, se usa el precio del producto.")
    @ApiResponse(responseCode = "201", description = "Creada en BORRADOR con su número correlativo")
    @ApiResponse(responseCode = "400", description = "Proveedor o productos inválidos, de otro proveedor o repetidos")
    public ResponseEntity<OrdenCompraResponseDTO> crear(@Valid @RequestBody OrdenCompraRequestDTO request) {
        OrdenCompraResponseDTO creada = ordenService.crear(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creada.id()).toUri();
        return ResponseEntity.created(location).body(creada);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permisos.ADMINISTRADOR_COMERCIAL)
    @Operation(summary = "Editar un borrador (observaciones y líneas)")
    @ApiResponse(responseCode = "409", description = "La orden ya no está en borrador")
    public OrdenCompraResponseDTO actualizar(@PathVariable Long id, @Valid @RequestBody OrdenCompraEdicionDTO request) {
        return ordenService.actualizar(id, request);
    }

    @PostMapping("/{id}/emitir")
    @PreAuthorize(Permisos.ADMINISTRADOR_COMERCIAL)
    @Operation(summary = "Emitir un borrador", description = "BORRADOR → EMITIDA. Desde aquí las líneas no cambian.")
    @ApiResponse(responseCode = "409", description = "No está en borrador o su catálogo cambió")
    public OrdenCompraResponseDTO emitir(@PathVariable Long id) {
        return ordenService.emitir(id);
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize(Permisos.ADMINISTRADOR_COMERCIAL)
    @Operation(summary = "Cancelar una orden", description = "Desde BORRADOR o EMITIDA sin mercancía recibida.")
    @ApiResponse(responseCode = "409", description = "Ya recibió mercancía o está en un estado final")
    public OrdenCompraResponseDTO cancelar(@PathVariable Long id, @Valid @RequestBody CancelacionOrdenDTO request) {
        return ordenService.cancelar(id, request);
    }

    @PostMapping("/desde-solicitud/{solicitudId}")
    @PreAuthorize(Permisos.ADMINISTRADOR_COMERCIAL)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generar borradores desde una solicitud aprobada",
            description = "Una orden por proveedor de los productos de la solicitud.")
    @ApiResponse(responseCode = "409", description = "No está aprobada, ya tiene órdenes o hay productos dados de baja")
    public List<OrdenCompraResponseDTO> crearDesdeSolicitud(@PathVariable Long solicitudId) {
        return ordenService.crearDesdeSolicitud(solicitudId);
    }

    @PutMapping("/{id}/entrega")
    @PreAuthorize(Permisos.ADMINISTRADOR_COMERCIAL)
    @Operation(summary = "Programar o reprogramar la entrega (ADMINISTRADOR_COMERCIAL)",
            description = "Fecha estimada (día, sin hora) y franja opcional. Cada cambio queda en el historial; "
                    + "reprogramar exige motivo. confirmadaProveedor distingue la fecha confirmada por el proveedor "
                    + "de una estimación interna. Repetir la programación vigente no crea una versión nueva.")
    @ApiResponse(responseCode = "400", description = "Fecha pasada, franja incoherente o falta el motivo")
    @ApiResponse(responseCode = "409", description = "La orden ya se recibió o se canceló")
    public ProgramacionEntregaDTO programarEntrega(@PathVariable Long id,
                                                   @Valid @RequestBody ProgramacionEntregaRequestDTO request) {
        return entregaService.programar(id, request);
    }

    @GetMapping("/{id}/entrega/historial")
    @PreAuthorize(Permisos.LECTURA_OPERATIVA)
    @Operation(summary = "Historial de la programación de la entrega, de la primera versión a la vigente")
    public List<ProgramacionEntregaDTO> historialEntrega(@PathVariable Long id) {
        return entregaService.historial(id);
    }

    @GetMapping("/{id}/historial")
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Historial completo de la orden",
            description = "Creación, aprobación de la solicitud de origen, emisión y documento, programaciones, "
                    + "recepciones con incidencias y movimientos de inventario, cancelación; en orden cronológico.")
    public List<EventoHistorialDTO> historial(@PathVariable Long id) {
        return historialService.deOrden(id);
    }

    @GetMapping(value = "/{id}/documento")
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Descargar el documento Word (.docx) de una orden emitida",
            description = "Se genera desde la instantánea inmutable guardada al emitir (versión vigente). "
                    + "Las órdenes emitidas antes de V13 se reconstruyen y el documento lo indica.")
    @ApiResponse(responseCode = "200", description = "Archivo .docx",
            content = @Content(mediaType = DocumentoOrdenService.TIPO_DOCX))
    @ApiResponse(responseCode = "404", description = "No existe en la empresa")
    @ApiResponse(responseCode = "409", description = "La orden no se ha emitido")
    public ResponseEntity<byte[]> documento(@PathVariable Long id) {
        return descarga(documentoService.descargar(id, DocumentoOrdenService.Formato.DOCX));
    }

    @GetMapping(value = "/{id}/documento/pdf")
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Descargar el PDF de una orden emitida (formato prioritario para compartir)",
            description = "Mismo contenido, versión y huella que el Word: ambos se generan desde la instantánea "
                    + "inmutable de la emisión. Las órdenes emitidas antes de V13 se reconstruyen y el PDF lo indica.")
    @ApiResponse(responseCode = "200", description = "Archivo PDF",
            content = @Content(mediaType = DocumentoOrdenService.TIPO_PDF))
    @ApiResponse(responseCode = "404", description = "No existe en la empresa")
    @ApiResponse(responseCode = "409", description = "La orden no se ha emitido")
    public ResponseEntity<byte[]> documentoPdf(@PathVariable Long id) {
        return descarga(documentoService.descargar(id, DocumentoOrdenService.Formato.PDF));
    }

    private static ResponseEntity<byte[]> descarga(DocumentoOrdenService.Archivo archivo) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.tipo()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(archivo.nombre(), StandardCharsets.UTF_8).build().toString())
                .header("X-Documento-Version", Integer.toString(archivo.version()))
                .header("X-Documento-Huella", archivo.huella())
                .header("X-Documento-Reconstruido", Boolean.toString(archivo.reconstruido()))
                .cacheControl(CacheControl.noStore())
                .body(archivo.contenido());
    }

    @GetMapping("/{id}/documentos")
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Versiones documentales de la orden", description = "Vacío si aún no se emitió o se emitió antes de V13.")
    public List<DocumentoOrdenDTO> documentos(@PathVariable Long id) {
        return documentoService.versiones(id);
    }

    @GetMapping("/{id}/recepciones")
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Recepciones de una orden, de la más antigua a la más reciente")
    public List<RecepcionResponseDTO> recepciones(@PathVariable Long id) {
        return recepcionService.deOrden(id);
    }

    @PostMapping("/{id}/recepciones")
    @PreAuthorize(Permisos.MIEMBRO_EMPRESA)
    @Operation(summary = "Registrar una recepción (parcial o total), ADMINISTRADOR_COMERCIAL o TRABAJADOR",
            description = "Suma las unidades aceptadas a las existencias y deja un movimiento por línea; las "
                    + "incidencias (faltantes, daños, excedentes, producto incorrecto) no mueven inventario. Idempotente "
                    + "por claveIdempotencia: repetirla con los mismos datos devuelve la recepción ya registrada (200). "
                    + "El trabajador recibe la respuesta sin costos.")
    @ApiResponse(responseCode = "201", description = "Registrada")
    @ApiResponse(responseCode = "200", description = "Ya estaba registrada con esa clave (no se duplica)")
    @ApiResponse(responseCode = "400", description = "Línea ajena a la orden, repetida o mayor que lo pendiente")
    @ApiResponse(responseCode = "409", description = "La orden no admite recepciones o la clave se usó con otros datos")
    public ResponseEntity<RecepcionResponseDTO> registrarRecepcion(@PathVariable Long id,
                                                                   @Valid @RequestBody RecepcionRequestDTO request) {
        ResultadoIdempotente<RecepcionResponseDTO> resultado = recepcionService.registrar(id, request);
        return ResponseEntity.status(resultado.nuevo() ? HttpStatus.CREATED : HttpStatus.OK).body(resultado.valor());
    }
}
