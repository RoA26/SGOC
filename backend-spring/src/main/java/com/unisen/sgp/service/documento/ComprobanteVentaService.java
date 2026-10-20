package com.unisen.sgp.service.documento;

import static com.unisen.sgp.service.documento.PdfComun.ACENTO;
import static com.unisen.sgp.service.documento.PdfComun.AVISO;
import static com.unisen.sgp.service.documento.PdfComun.MARGEN;
import static com.unisen.sgp.service.documento.PdfComun.TENUE;
import static com.unisen.sgp.service.documento.PdfComun.TINTA;
import static com.unisen.sgp.service.documento.PdfComun.celda;
import static com.unisen.sgp.service.documento.PdfComun.negrita;
import static com.unisen.sgp.service.documento.PdfComun.normal;
import static com.unisen.sgp.service.documento.PdfComun.pesos;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.unisen.sgp.config.NegocioProperties;
import com.unisen.sgp.model.dto.VentaDTO;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.EstadoVenta;
import com.unisen.sgp.model.entity.MedioPago;
import com.unisen.sgp.model.entity.TipoDescuento;
import com.unisen.sgp.model.entity.TratamientoIva;
import com.unisen.sgp.model.entity.Venta;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.security.UsuarioActual;
import com.unisen.sgp.service.comercial.VentaService;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comprobante interno de una venta en PDF. Se genera en cada descarga a partir de la venta
 * persistida, cuyos importes y líneas son una copia histórica inmutable: el comprobante no cambia
 * aunque cambien después los precios o el IVA del catálogo (solo refleja el estado actual:
 * devoluciones o anulación, con su fecha).
 *
 * <p><b>No es una factura electrónica ni un documento equivalente</b>: no lleva numeración
 * autorizada, CUFE ni validación de la DIAN, y lo dice en el propio documento. La facturación
 * electrónica es una fase independiente (ver docs/modulo-comercial.md).
 */
@Service
@Transactional(readOnly = true)
public class ComprobanteVentaService {

    private static final Logger log = LoggerFactory.getLogger(ComprobanteVentaService.class);
    static final String LEYENDA = "Comprobante interno de venta. No es factura electrónica ni documento equivalente; "
            + "no tiene validez ante la DIAN.";

    private final VentaService ventaService;
    private final ZoneId zona;
    private final DateTimeFormatter fechaHora;

    public ComprobanteVentaService(VentaService ventaService, NegocioProperties negocio) {
        this.ventaService = ventaService;
        this.zona = negocio.zonaHoraria();
        this.fechaHora = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", PdfComun.ES_CO);
    }

    public record Archivo(String nombre, byte[] contenido) {
    }

    /** Mismas reglas de visibilidad que el detalle: el trabajador, solo sus ventas (si no, 404). */
    @PreAuthorize(Permisos.LECTURA_OPERATIVA)
    public Archivo comprobante(Long ventaId) {
        Venta venta = ventaService.visible(ventaId);
        VentaDTO.Detalle detalle = ventaService.obtener(ventaId);
        byte[] pdf = generar(venta.getEmpresa(), detalle);
        log.info("Comprobante de la venta {} descargado por {}", detalle.numeroVisible(), UsuarioActual.paraAuditoria());
        return new Archivo(detalle.numeroVisible() + ".pdf", pdf);
    }

    byte[] generar(Empresa empresa, VentaDTO.Detalle v) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, MARGEN, MARGEN, MARGEN, MARGEN + 18);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            writer.setPageEvent(new PdfComun.Pie(v.numeroVisible() + "  ·  " + empresa.getNombre(), LEYENDA));
            doc.addTitle("Comprobante de venta " + v.numeroVisible());
            doc.addCreator("SGP · " + empresa.getNombre());
            doc.addCreationDate();
            doc.open();
            cabecera(doc, empresa, v);
            if (v.estado() != EstadoVenta.CONFIRMADA) {
                Paragraph estado = new Paragraph(estado(v), negrita(10f, AVISO));
                estado.setSpacingAfter(8f);
                doc.add(estado);
            }
            lineas(doc, v);
            totales(doc, v);
            pagos(doc, v);
            devoluciones(doc, v);
            Paragraph leyenda = new Paragraph(LEYENDA, negrita(8.5f, TENUE));
            leyenda.setSpacingBefore(14f);
            doc.add(leyenda);
            doc.close();
            return salida.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("No se pudo generar el comprobante de la venta.", ex);
        }
    }

    private void cabecera(Document doc, Empresa empresa, VentaDTO.Detalle v) throws DocumentException {
        PdfPTable tabla = PdfComun.tabla(58f, 42f);
        PdfPCell izquierda = new PdfPCell();
        izquierda.setBorder(Rectangle.NO_BORDER);
        izquierda.addElement(new Paragraph(empresa.getNombre(), negrita(16f, ACENTO)));
        izquierda.addElement(new Paragraph("NIT " + empresa.getNit(), normal(9.5f, TENUE)));
        izquierda.addElement(new Paragraph("Código empresarial " + empresa.getCodigo(), normal(9.5f, TENUE)));
        tabla.addCell(izquierda);
        PdfPCell derecha = new PdfPCell();
        derecha.setBorder(Rectangle.NO_BORDER);
        derecha.addElement(alinear(new Paragraph("COMPROBANTE INTERNO DE VENTA", negrita(8.5f, TENUE))));
        derecha.addElement(alinear(new Paragraph(v.numeroVisible(), negrita(20f, TINTA))));
        derecha.addElement(alinear(new Paragraph(fechaHora.format(v.vendidaEn().atZone(zona)), normal(9.5f, TENUE))));
        derecha.addElement(alinear(new Paragraph("Vendió: " + v.vendidaPor().nombre(), normal(9.5f, TENUE))));
        tabla.addCell(derecha);
        tabla.setSpacingAfter(8f);
        doc.add(tabla);
        if (v.clienteNombre() != null || v.clienteDocumento() != null) {
            String cliente = "Cliente: " + (v.clienteNombre() == null ? "" : v.clienteNombre())
                    + (v.clienteDocumento() == null ? "" : "  ·  Documento " + v.clienteDocumento());
            Paragraph p = new Paragraph(cliente, normal(9.5f, TINTA));
            p.setSpacingAfter(6f);
            doc.add(p);
        }
    }

    private static Paragraph alinear(Paragraph p) {
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private String estado(VentaDTO.Detalle v) {
        return switch (v.estado()) {
            case ANULADA -> "VENTA ANULADA el " + fechaHora.format(v.anuladaEn().atZone(zona)) + ". Motivo: "
                    + v.motivoAnulacion();
            case DEVUELTA -> "VENTA DEVUELTA EN SU TOTALIDAD (ver devoluciones al final).";
            case DEVUELTA_PARCIALMENTE -> "VENTA CON DEVOLUCIONES PARCIALES (ver devoluciones al final).";
            case CONFIRMADA -> "";
        };
    }

    private static void lineas(Document doc, VentaDTO.Detalle v) throws DocumentException {
        PdfPTable tabla = PdfComun.tabla(5f, 30f, 8f, 13f, 12f, 13f, 7f, 12f);
        tabla.setHeaderRows(1);
        tabla.setSplitRows(false);
        String[] encabezados = {"#", "Producto", "Cant.", "Precio unit.", "Descuento", "Base", "IVA", "Total"};
        for (int c = 0; c < encabezados.length; c++) {
            tabla.addCell(PdfComun.cabecera(encabezados[c], c >= 2));
        }
        for (int i = 0; i < v.lineas().size(); i++) {
            VentaDTO.Linea l = v.lineas().get(i);
            boolean alterna = i % 2 == 1;
            tabla.addCell(PdfComun.celdaLinea(String.valueOf(l.linea()), false, alterna));
            tabla.addCell(PdfComun.celdaLinea(l.nombre() + "\n" + l.sku(), false, alterna));
            tabla.addCell(PdfComun.celdaLinea(PdfComun.entero(l.cantidad()), true, alterna));
            tabla.addCell(PdfComun.celdaLinea(pesos(l.precioUnitario()), true, alterna));
            BigDecimal descuento = l.descuentoLinea().add(l.descuentoGeneral());
            tabla.addCell(PdfComun.celdaLinea(descuento.signum() == 0 ? "-" : "- " + pesos(descuento), true, alterna));
            tabla.addCell(PdfComun.celdaLinea(pesos(l.baseGravable()), true, alterna));
            tabla.addCell(PdfComun.celdaLinea(iva(l.tratamientoIva(), l.tarifaIva()), true, alterna));
            tabla.addCell(PdfComun.celdaLinea(pesos(l.total()), true, alterna));
        }
        doc.add(tabla);
        Paragraph nota = new Paragraph("Precios unitarios sin IVA. El descuento incluye el de la línea y su parte del "
                + "descuento general; el IVA se calcula sobre la base después de descuentos.", normal(7.5f, TENUE));
        nota.setSpacingAfter(8f);
        doc.add(nota);
    }

    private static String iva(TratamientoIva tratamiento, BigDecimal tarifa) {
        return switch (tratamiento) {
            case GRAVADO -> PdfComun.porcentaje(tarifa);
            case EXENTO -> "Exento";
            case EXCLUIDO -> "Excluido";
        };
    }

    private static void totales(Document doc, VentaDTO.Detalle v) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[] {60f, 25f, 15f});
        tabla.setWidthPercentage(100f);
        fila(tabla, "Subtotal (sin IVA)", pesos(v.subtotal()), false);
        if (v.descuentoLineas().signum() > 0) {
            fila(tabla, "Descuentos por línea", "- " + pesos(v.descuentoLineas()), false);
        }
        if (v.descuentoGeneral().signum() > 0) {
            String detalle = v.descuentoGeneralTipo() == TipoDescuento.PORCENTAJE
                    ? " (" + PdfComun.porcentaje(v.descuentoGeneralValor()) + ")" : "";
            fila(tabla, "Descuento general" + detalle, "- " + pesos(v.descuentoGeneral()), false);
        }
        fila(tabla, "Base gravable", pesos(v.baseGravable()), false);
        for (VentaDTO.ImpuestoPorTarifa t : v.impuestosPorTarifa()) {
            String etiqueta = switch (t.tratamiento()) {
                case GRAVADO -> "IVA " + PdfComun.porcentaje(t.tarifa()) + " sobre " + pesos(t.base());
                case EXENTO -> "Exento (IVA 0 %) sobre " + pesos(t.base());
                case EXCLUIDO -> "Excluido de IVA sobre " + pesos(t.base());
            };
            fila(tabla, etiqueta, pesos(t.impuesto()), false);
        }
        fila(tabla, "TOTAL COP", pesos(v.total()), true);
        tabla.setSpacingAfter(8f);
        doc.add(tabla);
    }

    private static void fila(PdfPTable tabla, String etiqueta, String valor, boolean destacado) {
        tabla.addCell(celda("", normal(9f, TINTA), Element.ALIGN_LEFT));
        tabla.addCell(celda(etiqueta, destacado ? negrita(10.5f, TINTA) : normal(9f, TENUE), Element.ALIGN_RIGHT));
        tabla.addCell(celda(valor, destacado ? negrita(11f, TINTA) : normal(9f, TINTA), Element.ALIGN_RIGHT));
    }

    private static void pagos(Document doc, VentaDTO.Detalle v) throws DocumentException {
        doc.add(new Paragraph(PdfComun.titulo("PAGOS")));
        for (VentaDTO.Pago p : v.pagos()) {
            String texto = medio(p.medio()) + ": " + pesos(p.valor())
                    + (p.recibido() == null ? "" : "  ·  Recibido " + pesos(p.recibido()) + "  ·  Cambio " + pesos(p.cambio()))
                    + (p.referencia() == null ? "" : "  ·  Ref. " + p.referencia());
            doc.add(new Paragraph(texto, normal(9f, TINTA)));
        }
    }

    private static String medio(MedioPago medio) {
        return switch (medio) {
            case EFECTIVO -> "Efectivo";
            case TARJETA_DEBITO -> "Tarjeta débito";
            case TARJETA_CREDITO -> "Tarjeta crédito";
            case TRANSFERENCIA -> "Transferencia";
            case OTRO -> "Otro medio";
        };
    }

    private void devoluciones(Document doc, VentaDTO.Detalle v) throws DocumentException {
        if (v.devoluciones().isEmpty()) {
            return;
        }
        Paragraph titulo = new Paragraph(PdfComun.titulo("DEVOLUCIONES Y ANULACIONES"));
        titulo.setSpacingBefore(10f);
        doc.add(titulo);
        for (VentaDTO.Devolucion d : v.devoluciones()) {
            doc.add(new Paragraph((d.tipo().name().equals("ANULACION") ? "Anulación" : "Devolución") + " del "
                    + fechaHora.format(d.registradaEn().atZone(zona)) + " por " + d.registradaPor().nombre() + ": "
                    + pesos(d.total()) + ". Motivo: " + d.motivo(), normal(9f, TINTA)));
            for (VentaDTO.LineaDevuelta l : d.lineas()) {
                doc.add(new Paragraph("    " + PdfComun.entero(l.cantidad()) + " × " + l.nombre() + " (" + pesos(l.total())
                        + (l.reingresaInventario() ? ", vuelve al inventario)" : ", no vuelve al inventario)"),
                        normal(8.5f, TENUE)));
            }
        }
        Paragraph total = new Paragraph("Total devuelto: " + pesos(v.totalDevuelto()), negrita(9.5f, TINTA));
        doc.add(total);
    }
}
