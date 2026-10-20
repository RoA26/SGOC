package com.unisen.sgp.service.documento;

import static com.unisen.sgp.service.documento.PdfComun.ACENTO;
import static com.unisen.sgp.service.documento.PdfComun.AVISO;
import static com.unisen.sgp.service.documento.PdfComun.FONDO_FILA;
import static com.unisen.sgp.service.documento.PdfComun.MARGEN;
import static com.unisen.sgp.service.documento.PdfComun.TENUE;
import static com.unisen.sgp.service.documento.PdfComun.TINTA;
import static com.unisen.sgp.service.documento.PdfComun.celda;
import static com.unisen.sgp.service.documento.PdfComun.cursiva;
import static com.unisen.sgp.service.documento.PdfComun.negrita;
import static com.unisen.sgp.service.documento.PdfComun.normal;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Convierte una {@link InstantaneaOrden} en PDF. Igual que el Word, no consulta nada: el PDF de
 * una versión documental es siempre el mismo, aunque cambien después el catálogo, el proveedor o
 * la empresa. La tabla de líneas repite su cabecera en cada página y una fila nunca se parte.
 *
 * <p>Preparado para un logo futuro: la cabecera es una tabla de dos columnas cuya celda izquierda
 * hoy lleva el nombre de la empresa (no existe logo en el modelo y no se inventa).
 */
public final class GeneradorPdfOrden {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", PdfComun.ES_CO);
    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", PdfComun.ES_CO);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm", PdfComun.ES_CO);

    private final ZoneId zona;

    public GeneradorPdfOrden(ZoneId zona) {
        this.zona = zona;
    }

    public byte[] generar(InstantaneaOrden datos, int version, String huella, Instant generadoEn) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, MARGEN, MARGEN, MARGEN, MARGEN + 18);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            writer.setPageEvent(new PdfComun.Pie(datos.orden().numeroVisible() + "  ·  Versión " + version
                    + " del documento  ·  Huella " + huella.substring(0, 16) + "...",
                    "Generado por SGP (Sistema de Gestión de Órdenes de Compra)."));
            doc.addTitle("Orden de compra " + datos.orden().numeroVisible());
            doc.addSubject(datos.comprador().nombre() + " - " + datos.proveedor().razonSocial());
            doc.addCreator("SGP · " + datos.comprador().nombre());
            doc.addCreationDate();
            doc.open();
            cabecera(doc, datos);
            if (datos.reconstruida()) {
                Paragraph aviso = new Paragraph("Documento reconstruido: la orden se emitió el "
                        + FECHA.format(datos.orden().emitidaEn().atZone(zona))
                        + ", antes de que el sistema guardara los documentos de emisión. Las líneas, cantidades y "
                        + "precios son los emitidos; los datos de la empresa y del proveedor son los vigentes al "
                        + "descargarlo.", cursiva(8.5f, AVISO));
                aviso.setSpacingAfter(8f);
                doc.add(aviso);
            }
            partes(doc, datos);
            lineas(doc, datos);
            observaciones(doc, datos);
            autorizacion(doc, datos);
            doc.close();
            return salida.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("No se pudo generar el PDF de la orden.", ex);
        }
    }

    private void cabecera(Document doc, InstantaneaOrden datos) throws DocumentException {
        PdfPTable tabla = PdfComun.tabla(58f, 42f);
        PdfPCell empresa = new PdfPCell();
        empresa.setBorder(Rectangle.NO_BORDER);
        empresa.addElement(new Paragraph(datos.comprador().nombre(), negrita(17f, ACENTO)));
        empresa.addElement(new Paragraph("NIT " + datos.comprador().nit(), normal(9.5f, TENUE)));
        empresa.addElement(new Paragraph("Código empresarial " + datos.comprador().codigo(), normal(9.5f, TENUE)));
        tabla.addCell(empresa);

        PdfPCell orden = new PdfPCell();
        orden.setBorder(Rectangle.NO_BORDER);
        orden.addElement(derecha(new Paragraph("ORDEN DE COMPRA", negrita(8.5f, TENUE))));
        orden.addElement(derecha(new Paragraph(datos.orden().numeroVisible(), negrita(20f, TINTA))));
        orden.addElement(derecha(new Paragraph("Emitida el " + FECHA_HORA.format(datos.orden().emitidaEn().atZone(zona)),
                normal(9.5f, TENUE))));
        tabla.addCell(orden);
        tabla.setSpacingAfter(12f);
        doc.add(tabla);
    }

    private static Paragraph derecha(Paragraph p) {
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private static void partes(Document doc, InstantaneaOrden datos) throws DocumentException {
        PdfPTable tabla = PdfComun.tabla(50f, 50f);
        PdfPCell proveedor = bloque("PROVEEDOR");
        InstantaneaOrden.Proveedor p = datos.proveedor();
        proveedor.addElement(new Paragraph(p.razonSocial(), negrita(10.5f, TINTA)));
        dato(proveedor, "NIT", p.nit());
        dato(proveedor, "Correo", p.email());
        dato(proveedor, "Teléfono", p.telefono());
        dato(proveedor, "Dirección", p.direccion());
        tabla.addCell(proveedor);

        PdfPCell entrega = bloque("ENTREGA");
        InstantaneaOrden.Entrega e = datos.entrega();
        if (e == null) {
            entrega.addElement(new Paragraph("Sin fecha programada al emitir la orden.", normal(9.5f, TENUE)));
        } else {
            dato(entrega, "Fecha estimada", FECHA.format(e.fechaEstimada()));
            dato(entrega, "Franja", e.horaDesde() == null ? "Todo el día (sin hora acordada)"
                    : HORA.format(e.horaDesde()) + " - " + HORA.format(e.horaHasta()));
            dato(entrega, "Estado", e.confirmadaProveedor() ? "Confirmada por el proveedor"
                    : "Estimada (pendiente de confirmar por el proveedor)");
            dato(entrega, "Referencia del proveedor", e.referenciaProveedor());
            dato(entrega, "Indicaciones", e.observaciones());
        }
        tabla.addCell(entrega);
        doc.add(tabla);
    }

    private static PdfPCell bloque(String titulo) {
        PdfPCell celda = new PdfPCell();
        celda.setBorder(Rectangle.BOX);
        celda.setBorderColor(java.awt.Color.WHITE);
        celda.setBorderWidth(3f);
        celda.setBackgroundColor(FONDO_FILA);
        celda.setPadding(8f);
        celda.addElement(new Paragraph(PdfComun.titulo(titulo)));
        return celda;
    }

    /** "Etiqueta: valor"; si el dato no existe en el sistema, no se escribe la línea. */
    private static void dato(PdfPCell celda, String etiqueta, String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        Paragraph p = new Paragraph();
        p.add(new Phrase(etiqueta + ": ", normal(8.5f, TENUE)));
        p.add(new Phrase(valor, normal(8.5f, TINTA)));
        celda.addElement(p);
    }

    private static void lineas(Document doc, InstantaneaOrden datos) throws DocumentException {
        List<InstantaneaOrden.Linea> lineas = datos.lineas();
        PdfPTable tabla = PdfComun.tabla(6f, 16f, 36f, 10f, 16f, 16f);
        tabla.setHeaderRows(1);
        tabla.setSplitLate(false);
        tabla.setSplitRows(false);
        String[] encabezados = {"N.º", "Código", "Descripción", "Cantidad", "Precio unitario", "Subtotal"};
        for (int c = 0; c < encabezados.length; c++) {
            tabla.addCell(PdfComun.cabecera(encabezados[c], c >= 3));
        }
        for (int i = 0; i < lineas.size(); i++) {
            InstantaneaOrden.Linea linea = lineas.get(i);
            boolean alterna = i % 2 == 1;
            tabla.addCell(PdfComun.celdaLinea(String.valueOf(linea.numero()), false, alterna));
            PdfPCell codigo = PdfComun.celdaLinea(linea.codigo(), false, alterna);
            if (linea.codigoBarras() != null) {
                codigo.setPhrase(new Phrase(linea.codigo() + "\n" + linea.codigoBarras(), normal(8.5f, TINTA)));
            }
            tabla.addCell(codigo);
            tabla.addCell(PdfComun.celdaLinea(linea.descripcion(), false, alterna));
            tabla.addCell(PdfComun.celdaLinea(PdfComun.entero(linea.cantidad()), true, alterna));
            tabla.addCell(PdfComun.celdaLinea(PdfComun.pesos(linea.precioUnitario()), true, alterna));
            tabla.addCell(PdfComun.celdaLinea(PdfComun.pesos(linea.subtotal()), true, alterna));
        }
        PdfPCell etiqueta = celda("Total " + datos.moneda(), negrita(10f, TINTA), Element.ALIGN_RIGHT);
        etiqueta.setColspan(5);
        tabla.addCell(etiqueta);
        tabla.addCell(celda(PdfComun.pesos(datos.total()), negrita(11f, TINTA), Element.ALIGN_RIGHT));
        doc.add(tabla);

        int unidades = lineas.stream().mapToInt(InstantaneaOrden.Linea::cantidad).sum();
        Paragraph nota = new Paragraph("Valores en pesos colombianos (" + datos.moneda() + "), sin impuestos ni "
                + "descuentos (la orden no los registra). " + PdfComun.entero(unidades) + " unidades en "
                + lineas.size() + (lineas.size() == 1 ? " línea." : " líneas."), normal(7.5f, TENUE));
        nota.setSpacingAfter(10f);
        doc.add(nota);
    }

    private static void observaciones(Document doc, InstantaneaOrden datos) throws DocumentException {
        String observaciones = datos.orden().observaciones();
        if (observaciones == null) {
            return;
        }
        doc.add(new Paragraph(PdfComun.titulo("OBSERVACIONES")));
        Paragraph texto = new Paragraph(observaciones, normal(9.5f, TINTA));
        texto.setSpacingAfter(10f);
        doc.add(texto);
    }

    private void autorizacion(Document doc, InstantaneaOrden datos) throws DocumentException {
        doc.add(new Paragraph(PdfComun.titulo("AUTORIZACIÓN")));
        doc.add(new Paragraph(datos.autorizacion().nombre(), negrita(10.5f, TINTA)));
        doc.add(new Paragraph(datos.autorizacion().rol() + " · " + datos.comprador().nombre(), normal(9.5f, TENUE)));
        doc.add(new Paragraph("Emitió la orden en el sistema el "
                + FECHA_HORA.format(datos.orden().emitidaEn().atZone(zona)) + ".", normal(8.5f, TENUE)));
        if (datos.orden().solicitudId() != null) {
            doc.add(new Paragraph("Origen: solicitud interna de compra #" + datos.orden().solicitudId() + ".",
                    normal(8.5f, TENUE)));
        }
    }
}
