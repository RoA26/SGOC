package com.unisen.sgp.service.documento;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unisen.sgp.config.NegocioProperties;
import com.unisen.sgp.exception.ConflictoException;
import com.unisen.sgp.exception.RecursoNoEncontradoException;
import com.unisen.sgp.model.dto.DocumentoOrdenDTO;
import com.unisen.sgp.model.entity.DetalleOrdenCompra;
import com.unisen.sgp.model.entity.DocumentoOrdenCompra;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.OrdenCompra;
import com.unisen.sgp.model.entity.ProgramacionEntrega;
import com.unisen.sgp.model.entity.ProveedorReferencia;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.DocumentoOrdenCompraRepository;
import com.unisen.sgp.repository.OrdenCompraRepository;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.security.UsuarioActual;
import com.unisen.sgp.service.OrdenCompraService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Documento Word de las órdenes emitidas.
 *
 * <p>Estrategia de almacenamiento: al emitir se guarda una instantánea inmutable (JSON + SHA-256)
 * en {@code documentos_orden_compra}, en la misma transacción que la emisión. El .docx se genera
 * desde ella en cada descarga: no hay almacenamiento de binarios y el documento no cambia aunque
 * después cambien el catálogo, el proveedor o la empresa. No se sobrescribe nunca: cada versión
 * es una fila nueva (hoy solo existe la de emisión porque una orden emitida no se modifica).
 */
@Service
@Transactional(readOnly = true)
public class DocumentoOrdenService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoOrdenService.class);
    public static final String TIPO_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /** Mapper propio: la instantánea no depende de la configuración JSON de la API. */
    private static final JsonMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    private final DocumentoOrdenCompraRepository documentoRepository;
    private final OrdenCompraRepository ordenRepository;
    private final GeneradorDocumentoOrden generador;
    private final Clock clock;

    public DocumentoOrdenService(DocumentoOrdenCompraRepository documentoRepository,
                                 OrdenCompraRepository ordenRepository, NegocioProperties negocio, Clock clock) {
        this.documentoRepository = documentoRepository;
        this.ordenRepository = ordenRepository;
        this.generador = new GeneradorDocumentoOrden(negocio.zonaHoraria());
        this.clock = clock;
    }

    /** Archivo listo para descargar. */
    public record Archivo(String nombre, byte[] contenido, int version, String huella, boolean reconstruido) {
    }

    /**
     * Guarda la versión 1 (emisión). Lo invoca {@link OrdenCompraService#emitir} dentro de su
     * transacción: si falla, la orden no queda emitida.
     */
    @Transactional
    public DocumentoOrdenCompra registrarEmision(OrdenCompra orden, Usuario emisor, Instant ahora) {
        InstantaneaOrden instantanea = instantanea(orden, false);
        String contenido = serializar(instantanea);
        DocumentoOrdenCompra documento = documentoRepository.save(new DocumentoOrdenCompra(orden, 1,
                DocumentoOrdenCompra.MOTIVO_EMISION, contenido, sha256(contenido), emisor, ahora));
        log.info("Documento v1 de la orden {} registrado (huella {})", orden.getNumero(), documento.getHuellaSha256());
        return documento;
    }

    /**
     * Genera el .docx de la versión vigente. Una orden emitida antes de V13 no tiene instantánea:
     * se arma con los datos actuales sin guardarla (una lectura no escribe) y el documento lo
     * advierte.
     *
     * @throws ConflictoException si la orden nunca se emitió
     */
    @PreAuthorize(Permisos.GESTION)
    public Archivo descargar(Long ordenId) {
        OrdenCompra orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> RecursoNoEncontradoException.ordenCompra(ordenId));
        if (orden.getEmitidaEn() == null) {
            throw new ConflictoException("La orden " + OrdenCompraService.numeroVisible(orden.getNumero())
                    + " no se ha emitido: el documento se genera al emitirla.");
        }
        String nombre = OrdenCompraService.numeroVisible(orden.getNumero()) + ".docx";
        var vigente = documentoRepository.findFirstByOrdenIdOrderByVersionDesc(ordenId);
        if (vigente.isPresent()) {
            DocumentoOrdenCompra documento = vigente.get();
            InstantaneaOrden instantanea = leer(documento.getContenido());
            byte[] bytes = generador.generar(instantanea, documento.getVersion(), documento.getHuellaSha256(),
                    documento.getGeneradoEn());
            log.info("Documento v{} de la orden {} descargado por {}", documento.getVersion(), orden.getNumero(),
                    UsuarioActual.paraAuditoria());
            return new Archivo(nombre, bytes, documento.getVersion(), documento.getHuellaSha256(), false);
        }
        InstantaneaOrden reconstruida = instantanea(orden, true);
        String huella = sha256(serializar(reconstruida));
        byte[] bytes = generador.generar(reconstruida, 1, huella, clock.instant().truncatedTo(ChronoUnit.SECONDS));
        log.info("Documento reconstruido de la orden {} (emitida antes de V13) descargado por {}", orden.getNumero(),
                UsuarioActual.paraAuditoria());
        return new Archivo(nombre, bytes, 1, huella, true);
    }

    /** Versiones documentales de la orden, para su historial (vacío si se emitió antes de V13). */
    @PreAuthorize(Permisos.GESTION)
    public List<DocumentoOrdenDTO> versiones(Long ordenId) {
        if (!ordenRepository.existsById(ordenId)) {
            throw RecursoNoEncontradoException.ordenCompra(ordenId);
        }
        return documentoRepository.findByOrdenIdOrderByVersionAsc(ordenId).stream().map(DocumentoOrdenDTO::from).toList();
    }

    /** Debe invocarse dentro de una transacción (recorre asociaciones LAZY). */
    InstantaneaOrden instantanea(OrdenCompra orden, boolean reconstruida) {
        Empresa empresa = orden.getEmpresa();
        ProveedorReferencia proveedor = orden.getProveedor();
        ProgramacionEntrega programacion = orden.getProgramacionEntrega();
        List<InstantaneaOrden.Linea> lineas = new ArrayList<>();
        int numero = 1;
        for (DetalleOrdenCompra detalle : orden.getDetalles()) {
            lineas.add(new InstantaneaOrden.Linea(numero++, detalle.getProducto().getSku(),
                    detalle.getProducto().getCodigoBarras(), detalle.getProducto().getNombre(), detalle.getCantidad(),
                    detalle.getCostoUnitario(), detalle.subtotal()));
        }
        Usuario emisor = orden.getEmitidaPor();
        return new InstantaneaOrden(
                InstantaneaOrden.FORMATO_ACTUAL,
                new InstantaneaOrden.Orden(orden.getId(), orden.getNumero(),
                        OrdenCompraService.numeroVisible(orden.getNumero()), orden.getEmitidaEn(),
                        orden.getSolicitudId(), orden.getObservaciones()),
                new InstantaneaOrden.Comprador(empresa.getNombre(), empresa.getNit(), empresa.getCodigo()),
                new InstantaneaOrden.Proveedor(proveedor.getNit(), proveedor.getRazonSocial(), proveedor.getEmail(),
                        proveedor.getTelefono(), proveedor.getDireccion()),
                programacion == null ? null : new InstantaneaOrden.Entrega(programacion.getFechaEstimada(),
                        programacion.getHoraDesde(), programacion.getHoraHasta(), programacion.isConfirmadaProveedor(),
                        programacion.getReferenciaProveedor(), programacion.getObservaciones()),
                lineas,
                orden.total(),
                InstantaneaOrden.MONEDA,
                new InstantaneaOrden.Autorizacion(emisor.getNombre(), etiquetaRol(emisor.getRol())),
                reconstruida);
    }

    static String etiquetaRol(Rol rol) {
        return switch (rol) {
            case ADMIN_EMPRESA -> "Administrador de empresa";
            case TRABAJADOR -> "Trabajador";
            case SUPER_ADMIN -> "Super administrador de la plataforma";
        };
    }

    static String serializar(InstantaneaOrden instantanea) {
        try {
            return JSON.writeValueAsString(instantanea);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar la instantánea de la orden.", ex);
        }
    }

    static InstantaneaOrden leer(String contenido) {
        try {
            return JSON.readValue(contenido, InstantaneaOrden.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("La instantánea guardada de la orden no es legible.", ex);
        }
    }

    static String sha256(String contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(contenido.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
