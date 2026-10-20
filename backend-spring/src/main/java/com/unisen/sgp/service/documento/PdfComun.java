package com.unisen.sgp.service.documento;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Piezas comunes de los PDF del sistema (OpenPDF): tipografía, colores, celdas, formato de pesos y
 * el pie con «Página X de Y». Helvetica es una de las 14 fuentes estándar de PDF: no se incrusta
 * nada y con la codificación Cp1252 cubre las tildes, la eñe y los signos del español.
 *
 * <p>Tamaño carta (la habitual en Colombia) con márgenes de 54 pt (0,75"), como el Word.
 */
final class PdfComun {

    static final Locale ES_CO = Locale.forLanguageTag("es-CO");

    static final Color TINTA = new Color(0x1F, 0x29, 0x33);
    static final Color TENUE = new Color(0x5F, 0x6B, 0x7A);
    static final Color ACENTO = new Color(0x1E, 0x3A, 0x5F);
    static final Color FONDO_FILA = new Color(0xF2, 0xF4, 0xF7);
    static final Color BORDE = new Color(0xD0, 0xD5, 0xDD);
    static final Color AVISO = new Color(0x8A, 0x4B, 0x08);

    static final float MARGEN = 54f;

    private static final BaseFont NORMAL = fuente(BaseFont.HELVETICA);
    private static final BaseFont NEGRITA = fuente(BaseFont.HELVETICA_BOLD);
    private static final BaseFont CURSIVA = fuente(BaseFont.HELVETICA_OBLIQUE);

    private PdfComun() {
    }

    private static BaseFont fuente(String nombre) {
        try {
            return BaseFont.createFont(nombre, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        } catch (IOException ex) {
            throw new UncheckedIOException("Fuente PDF no disponible: " + nombre, ex);
        }
    }

    static Font normal(float puntos, Color color) {
        return new Font(NORMAL, puntos, Font.NORMAL, color);
    }

    static Font negrita(float puntos, Color color) {
        return new Font(NEGRITA, puntos, Font.NORMAL, color);
    }

    static Font cursiva(float puntos, Color color) {
        return new Font(CURSIVA, puntos, Font.NORMAL, color);
    }

    /** Celda sin bordes con un texto. */
    static PdfPCell celda(String texto, Font fuente, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto == null ? "" : texto, fuente));
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setHorizontalAlignment(alineacion);
        celda.setPaddingTop(3f);
        celda.setPaddingBottom(5f);
        celda.setPaddingLeft(4f);
        celda.setPaddingRight(4f);
        return celda;
    }

    /** Celda de cabecera de tabla: fondo de acento y texto blanco. */
    static PdfPCell cabecera(String texto, boolean derecha) {
        PdfPCell celda = celda(texto, negrita(8.5f, Color.WHITE), derecha ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        celda.setBackgroundColor(ACENTO);
        return celda;
    }

    /** Celda de línea con filete inferior fino y fondo alterno. */
    static PdfPCell celdaLinea(String texto, boolean derecha, boolean alterna) {
        PdfPCell celda = celda(texto, normal(8.5f, TINTA), derecha ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        celda.setBorder(Rectangle.BOTTOM);
        celda.setBorderColor(BORDE);
        celda.setBorderWidth(0.5f);
        if (alterna) {
            celda.setBackgroundColor(FONDO_FILA);
        }
        return celda;
    }

    /** Tabla al 100 % del ancho útil con anchos relativos. */
    static PdfPTable tabla(float... anchos) {
        PdfPTable tabla = new PdfPTable(anchos);
        tabla.setWidthPercentage(100f);
        tabla.setSpacingBefore(4f);
        tabla.setSpacingAfter(8f);
        return tabla;
    }

    /** "$ 1.250.000" (sin decimales si son ceros), igual que la interfaz y el Word. */
    static String pesos(BigDecimal valor) {
        return GeneradorDocumentoOrden.pesos(valor);
    }

    static String entero(int valor) {
        return GeneradorDocumentoOrden.entero(valor);
    }

    static String porcentaje(BigDecimal valor) {
        DecimalFormatSymbols simbolos = DecimalFormatSymbols.getInstance(ES_CO);
        simbolos.setDecimalSeparator(',');
        return new DecimalFormat("0.##", simbolos).format(valor) + " %";
    }

    /**
     * Pie de cada página: una línea de identificación (número, versión, huella…) y «Página X de Y».
     * El total de páginas se escribe en una plantilla al cerrar el documento.
     */
    static final class Pie extends PdfPageEventHelper {

        private final String identificacion;
        private final String leyenda;
        private PdfTemplate total;

        Pie(String identificacion, String leyenda) {
            this.identificacion = identificacion;
            this.leyenda = leyenda;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(40, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float izquierda = document.left();
            float derecha = document.right();
            float y = document.bottom() - 18;
            cb.setColorStroke(BORDE);
            cb.setLineWidth(0.5f);
            cb.moveTo(izquierda, y + 10);
            cb.lineTo(derecha, y + 10);
            cb.stroke();
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(identificacion, normal(7.5f, TENUE)),
                    izquierda, y, 0);
            String pagina = "Página " + writer.getPageNumber() + " de ";
            Font fuente = normal(7.5f, TENUE);
            float anchoTotal = NORMAL.getWidthPoint("000", 7.5f);
            float anchoPagina = NORMAL.getWidthPoint(pagina, 7.5f);
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(pagina, fuente),
                    derecha - anchoTotal - anchoPagina, y, 0);
            cb.addTemplate(total, derecha - anchoTotal, y);
            if (leyenda != null) {
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(leyenda, normal(7f, TENUE)),
                        izquierda, y - 10, 0);
            }
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(NORMAL, 7.5f);
            total.setColorFill(TENUE);
            total.setTextMatrix(0, 0);
            // Al cerrar, el escritor ya apunta a la página siguiente a la última.
            total.showText(String.valueOf(writer.getPageNumber() - 1));
            total.endText();
        }
    }

    /** Título de sección en versalitas espaciadas. */
    static Phrase titulo(String texto) {
        Chunk chunk = new Chunk(texto, negrita(7.5f, TENUE));
        chunk.setCharacterSpacing(0.8f);
        return new Phrase(chunk);
    }
}
