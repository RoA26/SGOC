package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.support.ComprasApiTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * PDF de la orden emitida: archivo válido con los datos y totales de la orden, mismo documento
 * (versión y huella) que el Word, tablas largas en varias páginas, datos opcionales ausentes sin
 * inventar nada, permisos por rol y aislamiento entre empresas.
 */
class DocumentoPdfOrdenTest extends ComprasApiTest {

    private long proveedor;
    private long tornillo;
    private long tuerca;

    @BeforeEach
    void prepararCatalogo() throws Exception {
        String body = """
                {"nit": "900111111-1", "razonSocial": "Aceros Andinos", "email": "ventas@aceros.com",
                 "telefono": "6015550101", "direccion": "Calle 10 # 20-30, Bogotá"}
                """;
        proveedor = leer(postJson("/api/v1/proveedores", adminToken, body)).get("id").asLong();
        tornillo = crearProducto("TOR-01", "Tornillo hexagonal", "1250", proveedor);
        tuerca = crearProducto("TUE-01", "Tuerca", "300", proveedor);
    }

    @Test
    void elPdfEsValidoYCoincideConLaOrdenYConElWord() throws Exception {
        long id = crearOrden(adminToken, proveedor, new long[] {tornillo, 8}, new long[] {tuerca, 1500}).get("id").asLong();
        JsonNode emitida = leer(emitir(id, adminToken).andExpect(status().isOk()));

        MockHttpServletResponse pdf = getJson(ORDENES + "/" + id + "/documento/pdf", adminToken)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("OC-00001.pdf")))
                .andExpect(header().string("X-Documento-Version", "1"))
                .andExpect(header().string("X-Documento-Reconstruido", "false"))
                .andReturn().getResponse();
        byte[] bytes = pdf.getContentAsByteArray();
        assertThat(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        String texto = texto(bytes);
        assertThat(texto)
                .contains("Nexora", "NIT 900000001-1", "Código empresarial NEXO01")
                .contains("ORDEN DE COMPRA", "OC-00001")
                .contains("Aceros Andinos", "900111111-1", "ventas@aceros.com", "6015550101", "Calle 10 # 20-30, Bogotá")
                .contains("TOR-01", "Tornillo hexagonal", "TUE-01", "Tuerca")
                .contains("$ 10.000", "$ 450.000", "Total COP", "$ 460.000")
                .contains("Admin Nexora", "Administrador comercial")
                // El extractor de texto no conserva el espacio antes del total de páginas.
                .containsPattern("Página 1 de\\s?1\\b");
        // El total del PDF es el de la orden (misma regla: suma de líneas).
        assertThat(emitida.get("total").decimalValue()).isEqualByComparingTo("460000");
        assertThat(texto).doesNotContain("IVA").doesNotContain("Firma");

        // Word y PDF son el mismo documento: misma versión y misma huella de la instantánea.
        MockHttpServletResponse word = getJson(ORDENES + "/" + id + "/documento", adminToken)
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(pdf.getHeader("X-Documento-Huella")).isEqualTo(word.getHeader("X-Documento-Huella"));
        assertThat(contar("SELECT COUNT(*) FROM documentos_orden_compra WHERE orden_id = ?", id)).isEqualTo(1);
    }

    @Test
    void unaOrdenLargaOcupaVariasPaginasConLaCabeceraRepetida() throws Exception {
        List<long[]> lineas = new ArrayList<>();
        for (int i = 1; i <= 70; i++) {
            long producto = crearProducto("LARGO-%03d".formatted(i), "Producto de prueba número " + i, "1000", proveedor);
            lineas.add(new long[] {producto, i});
        }
        long id = crearOrden(adminToken, proveedor, lineas.toArray(long[][]::new)).get("id").asLong();
        emitir(id, adminToken).andExpect(status().isOk());

        byte[] bytes = getJson(ORDENES + "/" + id + "/documento/pdf", adminToken)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        PdfReader reader = new PdfReader(bytes);
        int paginas = reader.getNumberOfPages();
        assertThat(paginas).isGreaterThanOrEqualTo(2);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        String segunda = extractor.getTextFromPage(2);
        // La cabecera de la tabla se repite y el pie numera sobre el total.
        assertThat(segunda).contains("Descripción", "Precio unitario").containsPattern("Página 2 de\\s?" + paginas);
        String ultima = extractor.getTextFromPage(paginas);
        // 1 + 2 + … + 70 = 2485 unidades a $ 1.000 = $ 2.485.000.
        assertThat(ultima).contains("$ 2.485.000", "Producto de prueba número 70");
        reader.close();
    }

    @Test
    void losDatosOpcionalesAusentesNoSeInventan() throws Exception {
        long sinContacto = leer(postJson("/api/v1/proveedores", adminToken,
                json(Map.of("nit", "900222222-2", "razonSocial", "Proveedor Escueto",
                        "email", "escueto@proveedor.com")))).get("id").asLong();
        long producto = crearProducto("ESC-01", "Producto escueto", "500", sinContacto);
        long id = crearOrden(adminToken, sinContacto, new long[] {producto, 1}).get("id").asLong();
        emitir(id, adminToken).andExpect(status().isOk());

        String texto = texto(getJson(ORDENES + "/" + id + "/documento/pdf", adminToken)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(texto).contains("Proveedor Escueto", "Sin fecha programada")
                .doesNotContain("Teléfono:").doesNotContain("Dirección:").doesNotContain("OBSERVACIONES")
                .doesNotContain("Origen: solicitud");
    }

    @Test
    void respetaPermisosYAislamiento() throws Exception {
        long borrador = crearOrden(adminToken, proveedor, new long[] {tornillo, 2}).get("id").asLong();
        getJson(ORDENES + "/" + borrador + "/documento/pdf", adminToken).andExpect(status().isConflict());

        long id = ordenEmitida(proveedor, tornillo, 3).get("id").asLong();
        // El trabajador no descarga documentos con costos.
        getJson(ORDENES + "/" + id + "/documento/pdf", usuarioToken).andExpect(status().isForbidden());

        // El administrador comercial de otra empresa no la ve (404, sin revelar que existe).
        Empresa otra = crearEmpresa("Norte", "900000002-2");
        String adminOtra = crearUsuarioYEntrar("nora", Rol.ADMINISTRADOR_COMERCIAL, otra);
        var ajena = getJson(ORDENES + "/" + id + "/documento/pdf", adminOtra).andExpect(status().isNotFound())
                .andReturn().getResponse();
        assertThat(ajena.getContentAsString()).doesNotContain("Aceros").doesNotContain("Tornillo");

        // El SUPER_ADMIN, supervisando la empresa, puede consultarlo.
        String superAdmin = crearUsuarioYEntrar("root", Rol.SUPER_ADMIN, null);
        getEnEmpresa(ORDENES + "/" + id + "/documento/pdf", superAdmin, empresa.getId()).andExpect(status().isOk());
        getEnEmpresa(ORDENES + "/" + id + "/documento/pdf", superAdmin, otra.getId()).andExpect(status().isNotFound());
    }

    private static String texto(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder texto = new StringBuilder();
            for (int p = 1; p <= reader.getNumberOfPages(); p++) {
                texto.append(extractor.getTextFromPage(p)).append('\n');
            }
            return texto.toString();
        } finally {
            reader.close();
        }
    }
}
