package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.support.ComprasApiTest;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Documento Word de la orden emitida: archivo .docx válido con los datos emitidos, instantánea
 * inmutable (no cambia si cambia el catálogo), autorización y aislamiento entre empresas.
 */
class DocumentoOrdenTest extends ComprasApiTest {

    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

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
    void alEmitirSeGeneraUnDocxValidoConLosDatosDeLaOrden() throws Exception {
        long id = crearOrden(adminToken, proveedor, new long[] {tornillo, 8}, new long[] {tuerca, 1500}).get("id").asLong();
        programar(id, "2026-11-03", "08:00", "12:00", false, null);
        emitir(id, adminToken).andExpect(status().isOk());

        MockHttpServletResponse respuesta = getJson(ORDENES + "/" + id + "/documento", adminToken)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", DOCX))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("OC-00001.docx")))
                .andExpect(header().string("X-Documento-Version", "1"))
                .andExpect(header().string("X-Documento-Reconstruido", "false"))
                .andReturn().getResponse();
        byte[] bytes = respuesta.getContentAsByteArray();

        // Un .docx es un ZIP con word/document.xml; POI lo abre sin errores.
        assertThat(entradasZip(bytes)).contains("word/document.xml", "[Content_Types].xml", "word/footer1.xml");
        String texto = textoDe(bytes);
        assertThat(texto)
                .contains("Unisen", "NIT 900000001-1", "Código empresarial UNIS01")
                .contains("ORDEN DE COMPRA", "OC-00001")
                .contains("Aceros Andinos", "900111111-1", "ventas@aceros.com", "6015550101", "Calle 10 # 20-30, Bogotá")
                .contains("3 de noviembre de 2026", "08:00 – 12:00", "Estimada (pendiente de confirmar por el proveedor)")
                .contains("TOR-01", "Tornillo hexagonal", "TUE-01", "Tuerca")
                .contains("1.500", "$ 1.250", "$ 10.000", "$ 300", "$ 450.000")
                .contains("Total COP", "$ 460.000", "sin impuestos ni descuentos")
                .contains("Admin Unisen", "Administrador de empresa");
        // No se inventan datos que el sistema no tiene.
        assertThat(texto).doesNotContain("IVA").doesNotContain("Firma");

        getJson(ORDENES + "/" + id + "/documentos", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].motivo").value("EMISION"))
                .andExpect(jsonPath("$[0].generadoPor.username").value("admin"))
                .andExpect(jsonPath("$[0].huellaSha256").value(respuesta.getHeader("X-Documento-Huella")));
    }

    @Test
    void elDocumentoRepresentaLaVersionEmitidaAunqueCambieElCatalogo() throws Exception {
        long id = crearOrden(adminToken, proveedor, new long[] {tornillo, 2}).get("id").asLong();
        emitir(id, adminToken).andExpect(status().isOk());
        MockHttpServletResponse antes = getJson(ORDENES + "/" + id + "/documento", adminToken).andReturn().getResponse();

        // Después de emitir cambian el producto, el proveedor y la reprogramación de la entrega.
        putJson("/api/v1/productos/" + tornillo, adminToken, json(Map.of("sku", "TOR-99", "nombre", "Tornillo nuevo",
                "precio", 9999, "proveedorId", proveedor))).andExpect(status().isOk());
        putJson("/api/v1/proveedores/" + proveedor, adminToken, json(Map.of("nit", "900111111-1",
                "razonSocial", "Aceros Renombrados", "email", "otro@aceros.com"))).andExpect(status().isOk());
        programar(id, "2026-12-01", null, null, true, null);

        MockHttpServletResponse despues = getJson(ORDENES + "/" + id + "/documento", adminToken).andReturn().getResponse();
        assertThat(despues.getHeader("X-Documento-Huella")).isEqualTo(antes.getHeader("X-Documento-Huella"));
        String texto = textoDe(despues.getContentAsByteArray());
        assertThat(texto).contains("Tornillo hexagonal", "TOR-01", "Aceros Andinos", "$ 1.250", "Sin fecha programada")
                .doesNotContain("Tornillo nuevo").doesNotContain("Aceros Renombrados").doesNotContain("9.999");
        assertThat(contar("SELECT COUNT(*) FROM documentos_orden_compra WHERE orden_id = ?", id)).isEqualTo(1);
    }

    @Test
    void soloSeGeneraParaOrdenesEmitidas() throws Exception {
        long id = crearOrden(adminToken, proveedor, new long[] {tornillo, 2}).get("id").asLong();
        getJson(ORDENES + "/" + id + "/documento", adminToken).andExpect(status().isConflict());
        assertThat(contar("SELECT COUNT(*) FROM documentos_orden_compra")).isZero();

        cancelar(id, adminToken, "Ya no se necesita").andExpect(status().isOk());
        getJson(ORDENES + "/" + id + "/documento", adminToken).andExpect(status().isConflict());

        // Emitida y luego cancelada: el documento emitido sigue disponible (es un hecho histórico).
        long otra = ordenEmitida(proveedor, tornillo, 3).get("id").asLong();
        cancelar(otra, adminToken, "El proveedor no responde").andExpect(status().isOk());
        getJson(ORDENES + "/" + otra + "/documento", adminToken).andExpect(status().isOk());
    }

    @Test
    void ordenEmitidaAntesDeV13SeReconstruyeSinEscribirYLoAdvierte() throws Exception {
        long id = ordenEmitida(proveedor, tornillo, 4).get("id").asLong();
        jdbcTemplate.update("DELETE FROM documentos_orden_compra WHERE orden_id = ?", id);

        MockHttpServletResponse respuesta = getJson(ORDENES + "/" + id + "/documento", adminToken)
                .andExpect(status().isOk())
                .andExpect(header().string("X-Documento-Reconstruido", "true"))
                .andReturn().getResponse();
        assertThat(textoDe(respuesta.getContentAsByteArray())).contains("Documento reconstruido", "Tornillo hexagonal");
        // Una lectura no escribe: la orden sigue sin versión documental guardada.
        assertThat(contar("SELECT COUNT(*) FROM documentos_orden_compra")).isZero();
    }

    @Test
    void descargaAutorizadaPorRolYEmpresa() throws Exception {
        long id = ordenEmitida(proveedor, tornillo, 2).get("id").asLong();

        // El trabajador no ve costos: no descarga el documento.
        getJson(ORDENES + "/" + id + "/documento", usuarioToken).andExpect(status().isForbidden());
        // Sin token, 401.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get(ORDENES + "/" + id + "/documento")).andExpect(status().isUnauthorized());

        // El SUPER_ADMIN supervisando la empresa lo consulta (solo lectura).
        String plataforma = crearUsuarioYEntrar("plataforma", Rol.SUPER_ADMIN, null);
        getEnEmpresa(ORDENES + "/" + id + "/documento", plataforma, empresa.getId()).andExpect(status().isOk());

        // Otra empresa nunca la descarga manipulando el id, ni su administrador ni el SUPER_ADMIN supervisándola.
        Empresa norte = crearEmpresa("Norte", "900999999-9");
        String intruso = crearUsuarioYEntrar("admin.norte", Rol.ADMIN_EMPRESA, norte);
        getJson(ORDENES + "/" + id + "/documento", intruso).andExpect(status().isNotFound());
        getJson(ORDENES + "/" + id + "/documentos", intruso).andExpect(status().isNotFound());
        getEnEmpresa(ORDENES + "/" + id + "/documento", plataforma, norte.getId()).andExpect(status().isNotFound());
        getJson(ORDENES + "/999999/documento", adminToken).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ ayudas

    private void programar(long ordenId, String fecha, String desde, String hasta, boolean confirmada, String motivo)
            throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("fechaEstimada", fecha);
        body.put("horaDesde", desde);
        body.put("horaHasta", hasta);
        body.put("confirmadaProveedor", confirmada);
        body.put("motivo", motivo != null ? motivo : "Cambio de fecha");
        JsonNode r = leer(putJson(ORDENES + "/" + ordenId + "/entrega", adminToken, json(body)));
        assertThat(r.has("version")).as("programación registrada: %s", r).isTrue();
    }

    private static String textoDe(byte[] docx) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private static java.util.List<String> entradasZip(byte[] bytes) throws Exception {
        java.util.List<String> nombres = new java.util.ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entrada = zip.getNextEntry(); entrada != null; entrada = zip.getNextEntry()) {
                nombres.add(entrada.getName());
            }
        }
        return nombres;
    }
}
