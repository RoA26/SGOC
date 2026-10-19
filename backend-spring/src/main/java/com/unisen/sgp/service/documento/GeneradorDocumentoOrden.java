package com.unisen.sgp.service.documento;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableCell.XWPFVertAlign;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;

/**
 * Convierte una {@link InstantaneaOrden} en un documento Word (.docx, Office Open XML) con
 * Apache POI. No consulta nada: todo sale de la instantánea, así el documento de una orden es
 * siempre el mismo.
 *
 * <p>Plantilla: cabecera con la empresa compradora y el número; bloques de proveedor y entrega;
 * tabla de líneas con totales; observaciones; autorización y un pie con la versión, la huella y
 * la paginación. Tamaño carta, la habitual en Colombia.
 */
public final class GeneradorDocumentoOrden {

    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES_CO);
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", ES_CO);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm", ES_CO);

    private static final String FUENTE = "Calibri";
    private static final String TINTA = "1F2933";
    private static final String TENUE = "5F6B7A";
    private static final String ACENTO = "1E3A5F";
    private static final String FONDO_CABECERA = "1E3A5F";
    private static final String FONDO_FILA = "F2F4F7";
    private static final String BORDE = "D0D5DD";

    /** Ancho útil de la página carta con márgenes de 0,75": 12240 − 2 × 1080 veinteavos de punto. */
    private static final int ANCHO_UTIL = 10_080;
    /**
     * Anchos fijos (veinteavos de punto) de la tabla de líneas: N.º, código, descripción, cantidad,
     * precio, subtotal. Fijos para que Word y LibreOffice los respeten igual.
     */
    private static final int[] ANCHOS_LINEAS = {600, 1_600, 3_630, 1_000, 1_600, 1_650};

    private final ZoneId zona;

    public GeneradorDocumentoOrden(ZoneId zona) {
        this.zona = zona;
    }

    /**
     * @param version versión documental que se imprime en el pie
     * @param huella  SHA-256 de la instantánea (se imprime abreviada en el pie)
     */
    public byte[] generar(InstantaneaOrden datos, int version, String huella, Instant generadoEn) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            configurarPagina(doc);
            propiedades(doc, datos, generadoEn);
            cabecera(doc, datos);
            if (datos.reconstruida()) {
                avisoReconstruida(doc, datos);
            }
            partes(doc, datos);
            lineas(doc, datos);
            observaciones(doc, datos);
            autorizacion(doc, datos);
            pie(doc, datos, version, huella);
            doc.write(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo generar el documento de la orden.", ex);
        }
    }

    // ------------------------------------------------------------------ secciones

    private static void configurarPagina(XWPFDocument doc) {
        CTSectPr seccion = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        CTPageSz tamano = seccion.isSetPgSz() ? seccion.getPgSz() : seccion.addNewPgSz();
        tamano.setW(BigInteger.valueOf(12_240)); // carta: 8,5" × 11" en veinteavos de punto
        tamano.setH(BigInteger.valueOf(15_840));
        CTPageMar margenes = seccion.isSetPgMar() ? seccion.getPgMar() : seccion.addNewPgMar();
        margenes.setTop(BigInteger.valueOf(1_080));
        margenes.setBottom(BigInteger.valueOf(1_080));
        margenes.setLeft(BigInteger.valueOf(1_080));
        margenes.setRight(BigInteger.valueOf(1_080));
        margenes.setFooter(BigInteger.valueOf(500));
        margenes.setHeader(BigInteger.valueOf(500));
    }

    private static void propiedades(XWPFDocument doc, InstantaneaOrden datos, Instant generadoEn) {
        var core = doc.getProperties().getCoreProperties();
        core.setTitle("Orden de compra " + datos.orden().numeroVisible());
        core.setSubjectProperty(datos.comprador().nombre() + " → " + datos.proveedor().razonSocial());
        core.setCreator("SGP · " + datos.comprador().nombre());
        core.setCreated(Optional.of(Date.from(generadoEn)));
    }

    private void cabecera(XWPFDocument doc, InstantaneaOrden datos) {
        XWPFTable tabla = tabla(doc, 1, new int[] {ANCHO_UTIL * 58 / 100, ANCHO_UTIL * 42 / 100});
        sinBordes(tabla);
        XWPFTableCell empresa = celda(tabla, 0, 0, Integer.toString(ANCHO_UTIL * 58 / 100));
        XWPFParagraph nombre = primerParrafo(empresa);
        texto(nombre, datos.comprador().nombre(), 18, true, ACENTO);
        XWPFParagraph nit = empresa.addParagraph();
        texto(nit, "NIT " + datos.comprador().nit(), 10, false, TENUE);
        XWPFParagraph codigo = empresa.addParagraph();
        texto(codigo, "Código empresarial " + datos.comprador().codigo(), 10, false, TENUE);

        XWPFTableCell orden = celda(tabla, 0, 1, Integer.toString(ANCHO_UTIL * 42 / 100));
        XWPFParagraph etiqueta = primerParrafo(orden);
        etiqueta.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun runEtiqueta = texto(etiqueta, "ORDEN DE COMPRA", 9, true, TENUE);
        runEtiqueta.setCharacterSpacing(40);
        XWPFParagraph numero = orden.addParagraph();
        numero.setAlignment(ParagraphAlignment.RIGHT);
        texto(numero, datos.orden().numeroVisible(), 22, true, TINTA);
        XWPFParagraph fecha = orden.addParagraph();
        fecha.setAlignment(ParagraphAlignment.RIGHT);
        texto(fecha, "Emitida el " + FECHA_HORA.format(datos.orden().emitidaEn().atZone(zona)), 10, false, TENUE);

        XWPFParagraph regla = doc.createParagraph();
        regla.setBorderBottom(Borders.SINGLE);
        regla.setSpacingAfter(240);
    }

    private void avisoReconstruida(XWPFDocument doc, InstantaneaOrden datos) {
        XWPFParagraph aviso = doc.createParagraph();
        aviso.setSpacingAfter(200);
        XWPFRun run = texto(aviso, "Documento reconstruido: la orden se emitió el "
                + FECHA.format(datos.orden().emitidaEn().atZone(zona))
                + ", antes de que el sistema guardara los documentos de emisión. Las líneas, cantidades y "
                + "precios son los emitidos (no cambian tras la emisión); los datos de la empresa y del proveedor "
                + "son los vigentes al descargarlo.", 9, false, TENUE);
        run.setItalic(true);
    }

    private static void partes(XWPFDocument doc, InstantaneaOrden datos) {
        XWPFTable tabla = tabla(doc, 1, 2);
        sinBordes(tabla);
        XWPFTableCell proveedor = celda(tabla, 0, 0, Integer.toString(ANCHO_UTIL / 2));
        proveedor.setColor(FONDO_FILA);
        titulo(primerParrafo(proveedor), "PROVEEDOR");
        InstantaneaOrden.Proveedor p = datos.proveedor();
        XWPFParagraph razon = proveedor.addParagraph();
        texto(razon, p.razonSocial(), 11, true, TINTA);
        dato(proveedor, "NIT", p.nit());
        dato(proveedor, "Correo", p.email());
        dato(proveedor, "Teléfono", p.telefono());
        dato(proveedor, "Dirección", p.direccion());

        XWPFTableCell entrega = celda(tabla, 0, 1, Integer.toString(ANCHO_UTIL / 2));
        entrega.setColor(FONDO_FILA);
        titulo(primerParrafo(entrega), "ENTREGA");
        InstantaneaOrden.Entrega e = datos.entrega();
        if (e == null) {
            XWPFParagraph sin = entrega.addParagraph();
            texto(sin, "Sin fecha programada al emitir la orden.", 10, false, TENUE);
        } else {
            XWPFParagraph fecha = entrega.addParagraph();
            texto(fecha, "Fecha estimada: ", 10, true, TINTA);
            texto(fecha, FECHA.format(e.fechaEstimada()), 10, false, TINTA);
            dato(entrega, "Franja", e.horaDesde() == null ? "Todo el día (sin hora acordada)"
                    : HORA.format(e.horaDesde()) + " – " + HORA.format(e.horaHasta()));
            dato(entrega, "Estado", e.confirmadaProveedor() ? "Confirmada por el proveedor"
                    : "Estimada (pendiente de confirmar por el proveedor)");
            dato(entrega, "Referencia del proveedor", e.referenciaProveedor());
            dato(entrega, "Indicaciones", e.observaciones());
        }
        espacio(doc, 160);
    }

    private static void lineas(XWPFDocument doc, InstantaneaOrden datos) {
        List<InstantaneaOrden.Linea> lineas = datos.lineas();
        // Cabecera + líneas + total.
        XWPFTable tabla = tabla(doc, lineas.size() + 2, ANCHOS_LINEAS);
        bordesFinos(tabla);
        String[] encabezados = {"N.º", "Código", "Descripción", "Cantidad", "Precio unitario", "Subtotal"};
        XWPFTableRow cabecera = tabla.getRow(0);
        cabecera.setRepeatHeader(true);
        for (int c = 0; c < encabezados.length; c++) {
            XWPFTableCell celda = celda(tabla, 0, c, Integer.toString(ANCHOS_LINEAS[c]));
            celda.setColor(FONDO_CABECERA);
            XWPFParagraph p = primerParrafo(celda);
            p.setAlignment(c >= 3 ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT);
            texto(p, encabezados[c], 9, true, "FFFFFF");
        }
        for (int i = 0; i < lineas.size(); i++) {
            InstantaneaOrden.Linea linea = lineas.get(i);
            int fila = i + 1;
            XWPFTableRow row = tabla.getRow(fila);
            row.setCantSplitRow(true);
            String fondo = i % 2 == 1 ? FONDO_FILA : null;
            celdaTexto(tabla, fila, 0, String.valueOf(linea.numero()), false, fondo);
            XWPFTableCell codigo = celdaTexto(tabla, fila, 1, linea.codigo(), false, fondo);
            if (linea.codigoBarras() != null) {
                texto(codigo.addParagraph(), linea.codigoBarras(), 8, false, TENUE);
            }
            celdaTexto(tabla, fila, 2, linea.descripcion(), false, fondo);
            celdaTexto(tabla, fila, 3, entero(linea.cantidad()), true, fondo);
            celdaTexto(tabla, fila, 4, pesos(linea.precioUnitario()), true, fondo);
            celdaTexto(tabla, fila, 5, pesos(linea.subtotal()), true, fondo);
        }
        int filaTotal = lineas.size() + 1;
        XWPFTableRow total = tabla.getRow(filaTotal);
        for (int c = 0; c < ANCHOS_LINEAS.length; c++) {
            celda(tabla, filaTotal, c, Integer.toString(ANCHOS_LINEAS[c]));
        }
        XWPFParagraph etiqueta = primerParrafo(total.getCell(4));
        etiqueta.setAlignment(ParagraphAlignment.RIGHT);
        texto(etiqueta, "Total " + datos.moneda(), 10, true, TINTA);
        XWPFParagraph valor = primerParrafo(total.getCell(5));
        valor.setAlignment(ParagraphAlignment.RIGHT);
        texto(valor, pesos(datos.total()), 11, true, TINTA);

        XWPFParagraph nota = doc.createParagraph();
        nota.setSpacingBefore(80);
        texto(nota, "Valores en pesos colombianos (" + datos.moneda() + "), sin impuestos ni descuentos. "
                + entero(lineas.stream().mapToInt(InstantaneaOrden.Linea::cantidad).sum()) + " unidades en "
                + lineas.size() + (lineas.size() == 1 ? " línea." : " líneas."), 8, false, TENUE);
        espacio(doc, 120);
    }

    private static void observaciones(XWPFDocument doc, InstantaneaOrden datos) {
        String observaciones = datos.orden().observaciones();
        if (observaciones == null) {
            return;
        }
        titulo(doc.createParagraph(), "OBSERVACIONES");
        for (String parrafo : observaciones.split("\\R")) {
            XWPFParagraph p = doc.createParagraph();
            p.setSpacingAfter(40);
            texto(p, parrafo, 10, false, TINTA);
        }
        espacio(doc, 120);
    }

    private void autorizacion(XWPFDocument doc, InstantaneaOrden datos) {
        titulo(doc.createParagraph(), "AUTORIZACIÓN");
        XWPFParagraph p = doc.createParagraph();
        texto(p, datos.autorizacion().nombre(), 11, true, TINTA);
        XWPFParagraph rol = doc.createParagraph();
        texto(rol, datos.autorizacion().rol() + " · " + datos.comprador().nombre(), 10, false, TENUE);
        XWPFParagraph cuando = doc.createParagraph();
        texto(cuando, "Emitió la orden en el sistema el " + FECHA_HORA.format(datos.orden().emitidaEn().atZone(zona))
                + ".", 9, false, TENUE);
        if (datos.orden().solicitudId() != null) {
            XWPFParagraph origen = doc.createParagraph();
            texto(origen, "Origen: solicitud interna de compra #" + datos.orden().solicitudId() + ".", 9, false, TENUE);
        }
    }

    private static void pie(XWPFDocument doc, InstantaneaOrden datos, int version, String huella) {
        XWPFFooter pie = doc.createFooter(HeaderFooterType.DEFAULT);
        XWPFParagraph p = pie.createParagraph();
        p.setBorderTop(Borders.SINGLE);
        texto(p, datos.orden().numeroVisible() + " · Versión " + version + " del documento · Huella "
                + huella.substring(0, 16) + "…  ·  Página ", 8, false, TENUE);
        campo(p, "PAGE", "1");
        texto(p, " de ", 8, false, TENUE);
        campo(p, "NUMPAGES", "1");
        XWPFParagraph generado = pie.createParagraph();
        texto(generado, "Generado por SGP (Sistema de Gestión de Órdenes de Compra).", 7, false, TENUE);
    }

    // ------------------------------------------------------------------ ayudas

    private static XWPFTable tabla(XWPFDocument doc, int filas, int columnas) {
        int[] anchos = new int[columnas];
        java.util.Arrays.fill(anchos, ANCHO_UTIL / columnas);
        return tabla(doc, filas, anchos);
    }

    /** Tabla de diseño fijo: cada columna con su ancho, igual en Word y en LibreOffice. */
    private static XWPFTable tabla(XWPFDocument doc, int filas, int[] anchos) {
        XWPFTable tabla = doc.createTable(filas, anchos.length);
        tabla.setWidth(Integer.toString(ANCHO_UTIL));
        tabla.setTableAlignment(TableRowAlign.CENTER);
        tabla.setCellMargins(60, 100, 60, 100);
        var propiedades = tabla.getCTTbl().getTblPr();
        (propiedades.isSetTblLayout() ? propiedades.getTblLayout() : propiedades.addNewTblLayout())
                .setType(STTblLayoutType.FIXED);
        CTTblGrid rejilla = tabla.getCTTbl().getTblGrid() != null ? tabla.getCTTbl().getTblGrid()
                : tabla.getCTTbl().addNewTblGrid();
        while (rejilla.sizeOfGridColArray() > 0) {
            rejilla.removeGridCol(0);
        }
        for (int ancho : anchos) {
            rejilla.addNewGridCol().setW(BigInteger.valueOf(ancho));
        }
        for (XWPFTableRow fila : tabla.getRows()) {
            for (int c = 0; c < anchos.length; c++) {
                fila.getCell(c).setWidth(Integer.toString(anchos[c]));
            }
        }
        return tabla;
    }

    private static void sinBordes(XWPFTable tabla) {
        tabla.setTopBorder(XWPFBorderType.NONE, 0, 0, "auto");
        tabla.setBottomBorder(XWPFBorderType.NONE, 0, 0, "auto");
        tabla.setLeftBorder(XWPFBorderType.NONE, 0, 0, "auto");
        tabla.setRightBorder(XWPFBorderType.NONE, 0, 0, "auto");
        tabla.setInsideHBorder(XWPFBorderType.NONE, 0, 0, "auto");
        tabla.setInsideVBorder(XWPFBorderType.NONE, 0, 0, "auto");
    }

    private static void bordesFinos(XWPFTable tabla) {
        tabla.setTopBorder(XWPFBorderType.SINGLE, 4, 0, BORDE);
        tabla.setBottomBorder(XWPFBorderType.SINGLE, 4, 0, BORDE);
        tabla.setLeftBorder(XWPFBorderType.NONE, 0, 0, "auto");
        tabla.setRightBorder(XWPFBorderType.NONE, 0, 0, "auto");
        tabla.setInsideHBorder(XWPFBorderType.SINGLE, 4, 0, BORDE);
        tabla.setInsideVBorder(XWPFBorderType.NONE, 0, 0, "auto");
    }

    private static XWPFTableCell celda(XWPFTable tabla, int fila, int columna, String ancho) {
        XWPFTableCell celda = tabla.getRow(fila).getCell(columna);
        celda.setWidth(ancho);
        celda.setVerticalAlignment(XWPFVertAlign.TOP);
        return celda;
    }

    private static XWPFTableCell celdaTexto(XWPFTable tabla, int fila, int columna, String valor, boolean derecha,
                                            String fondo) {
        XWPFTableCell celda = celda(tabla, fila, columna, Integer.toString(ANCHOS_LINEAS[columna]));
        if (fondo != null) {
            celda.setColor(fondo);
        }
        XWPFParagraph p = primerParrafo(celda);
        p.setAlignment(derecha ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT);
        texto(p, valor, 9, false, TINTA);
        return celda;
    }

    private static XWPFParagraph primerParrafo(XWPFTableCell celda) {
        XWPFParagraph p = celda.getParagraphs().get(0);
        p.setSpacingAfter(0);
        return p;
    }

    private static void titulo(XWPFParagraph p, String titulo) {
        p.setSpacingAfter(60);
        XWPFRun run = texto(p, titulo, 8, true, TENUE);
        run.setCharacterSpacing(30);
    }

    /** "Etiqueta: valor"; si el dato no existe en el sistema, no se escribe la línea. */
    private static void dato(XWPFTableCell celda, String etiqueta, String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        XWPFParagraph p = celda.addParagraph();
        p.setSpacingAfter(0);
        texto(p, etiqueta + ": ", 9, false, TENUE);
        texto(p, valor, 9, false, TINTA);
    }

    private static XWPFRun texto(XWPFParagraph p, String valor, int puntos, boolean negrita, String color) {
        XWPFRun run = p.createRun();
        run.setFontFamily(FUENTE);
        run.setFontSize(puntos);
        run.setBold(negrita);
        run.setColor(color);
        run.setText(valor);
        return run;
    }

    /**
     * Campo de Word (PAGE, NUMPAGES) como secuencia begin/instr/separate/valor/end: cada parte es
     * un run con el mismo estilo que el texto del pie, así el número no cambia de tamaño al
     * actualizarse.
     */
    private static void campo(XWPFParagraph p, String instruccion, String provisional) {
        estiloPie(p.createRun()).getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
        var instr = estiloPie(p.createRun()).getCTR().addNewInstrText();
        instr.setStringValue(" " + instruccion + " ");
        instr.setSpace(org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute.Space.PRESERVE);
        estiloPie(p.createRun()).getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
        estiloPie(p.createRun()).setText(provisional);
        estiloPie(p.createRun()).getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
    }

    private static XWPFRun estiloPie(XWPFRun run) {
        run.setFontFamily(FUENTE);
        run.setFontSize(8);
        run.setColor(TENUE);
        return run;
    }

    private static void espacio(XWPFDocument doc, int veinteavos) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(veinteavos);
    }

    /** "$ 1.250.000" (sin decimales si son ceros), igual que la interfaz. */
    static String pesos(BigDecimal valor) {
        DecimalFormatSymbols simbolos = DecimalFormatSymbols.getInstance(ES_CO);
        simbolos.setGroupingSeparator('.');
        simbolos.setDecimalSeparator(',');
        boolean entero = valor.stripTrailingZeros().scale() <= 0;
        return "$ " + new DecimalFormat(entero ? "#,##0" : "#,##0.00", simbolos).format(valor);
    }

    static String entero(int valor) {
        DecimalFormatSymbols simbolos = DecimalFormatSymbols.getInstance(ES_CO);
        simbolos.setGroupingSeparator('.');
        return new DecimalFormat("#,##0", simbolos).format(valor);
    }
}
