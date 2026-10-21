package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.support.VentasApiTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Reportes con datos reales (valores conocidos, devoluciones, anulaciones y costos desconocidos) y
 * comprobantes PDF (contenido, totales, visibilidad y aislamiento).
 */
class ReporteYComprobanteVentaTest extends VentasApiTest {

    private static final String REPORTE = "/api/v1/reportes/ventas";

    private long proveedor;
    private long camisa;
    private long arroz;

    @BeforeEach
    void prepararCatalogo() throws Exception {
        proveedor = crearProveedor("900111111-1", "Textiles del Valle");
        camisa = productoALaVenta(proveedor, "CAM-01", "10000", "GRAVADO", "19", 20, "7000");
        arroz = productoALaVenta(proveedor, "ARR-01", "2500", "EXCLUIDO", "0", 20, "2000");
    }

    @Test
    void elReporteSumaVentasRestaDevolucionesYExcluyeAnuladas() throws Exception {
        politica("10", "30", "PERMITIR").andExpect(status().isOk());
        // Venta 1 (trabajador): 2 camisas → base 20.000, costo 14.000.
        long v1 = venderOk(usuarioToken, lineas(linea(camisa, 2))).get("id").asLong();
        // Venta 2 (admin): 4 arroz con 10 % → base 9.000, costo 8.000, descuento 1.000.
        venderOk(adminToken, lineas(linea(arroz, 4, "PORCENTAJE", "10")));
        // Venta 3: anulada (no cuenta).
        long v3 = venderOk(usuarioToken, lineas(linea(camisa, 5))).get("id").asLong();
        postJson(VENTAS + "/" + v3 + "/anulacion", adminToken, json(Map.of("claveIdempotencia", clave(),
                "motivo", "Error"))).andExpect(status().isCreated());
        // Devolución de 1 camisa de la venta 1: −10.000 de ingreso y −7.000 de costo.
        long lineaV1 = leer(getJson(VENTAS + "/" + v1, adminToken)).get("lineas").get(0).get("id").asLong();
        devolver(v1, clave(), List.of(lineaDevuelta(lineaV1, 1, true))).andExpect(status().isCreated());

        JsonNode reporte = leer(getJson(REPORTE, adminToken).andExpect(status().isOk()));
        JsonNode r = reporte.get("resumen");
        assertThat(r.get("numeroVentas").asLong()).isEqualTo(2);
        assertThat(r.get("ventasAnuladas").asLong()).isEqualTo(1);
        assertThat(r.get("numeroDevoluciones").asLong()).isEqualTo(1);
        assertThat(r.get("ingresoBruto").decimalValue()).isEqualByComparingTo("29000");
        assertThat(r.get("devoluciones").decimalValue()).isEqualByComparingTo("10000");
        assertThat(r.get("ingresoNeto").decimalValue()).isEqualByComparingTo("19000");
        assertThat(r.get("costo").decimalValue()).isEqualByComparingTo("15000");
        assertThat(r.get("utilidadBruta").decimalValue()).isEqualByComparingTo("4000");
        assertThat(r.get("margenBruto").decimalValue()).isEqualByComparingTo("21.05");
        assertThat(r.get("recargo").decimalValue()).isEqualByComparingTo("26.67");
        assertThat(r.get("descuentos").decimalValue()).isEqualByComparingTo("1000");
        assertThat(r.get("costoCompleto").asBoolean()).isTrue();
        assertThat(reporte.get("metodoValoracion").asText()).isEqualTo("PROMEDIO_PONDERADO");
        assertThat(reporte.get("valoracionProvisional").asBoolean()).isTrue();

        // Por producto, netos de devoluciones: camisa 1 unidad (utilidad 3.000), arroz 4 (utilidad 1.000).
        assertThat(reporte.get("masVendidos").get(0).get("sku").asText()).isEqualTo("ARR-01");
        assertThat(reporte.get("masVendidos").get(0).get("unidades").asLong()).isEqualTo(4);
        assertThat(reporte.get("mayorUtilidad").get(0).get("sku").asText()).isEqualTo("CAM-01");
        assertThat(reporte.get("mayorUtilidad").get(0).get("utilidad").decimalValue()).isEqualByComparingTo("3000");
        // Por vendedor: el trabajador 20.000 − 10.000; el administrador 9.000.
        assertThat(reporte.get("porVendedor").size()).isEqualTo(2);
        // La serie diaria suma el ingreso neto.
        java.math.BigDecimal serie = java.math.BigDecimal.ZERO;
        for (JsonNode p : reporte.get("serie")) {
            serie = serie.add(p.get("ingresoNeto").decimalValue());
        }
        assertThat(serie).isEqualByComparingTo("19000");

        // Mismo resultado que el detalle de la venta 1 (mismos datos históricos).
        getJson(VENTAS + "/" + v1, adminToken).andExpect(jsonPath("$.rentabilidad.ingresoNeto").value(10000))
                .andExpect(jsonPath("$.rentabilidad.utilidad").value(3000));
    }

    @Test
    void sinCostoFiableLaUtilidadNoEstaDisponible() throws Exception {
        long sinCosto = productoALaVenta(proveedor, "SIN-01", "1000", "EXCLUIDO", "0", 5, null);
        venderOk(usuarioToken, lineas(linea(sinCosto, 2), linea(arroz, 1)));
        JsonNode r = leer(getJson(REPORTE, adminToken)).get("resumen");
        assertThat(r.get("ingresoNeto").decimalValue()).isEqualByComparingTo("4500");
        assertThat(r.get("costoCompleto").asBoolean()).isFalse();
        assertThat(r.get("ingresoSinCosto").decimalValue()).isEqualByComparingTo("2000");
        assertThat(r.get("utilidadBruta")).isNull();
        assertThat(r.get("margenBruto")).isNull();
    }

    @Test
    void sinVentasLosIndicadoresSonCeroONoDefinidos() throws Exception {
        JsonNode r = leer(getJson(REPORTE, adminToken)).get("resumen");
        assertThat(r.get("numeroVentas").asLong()).isZero();
        assertThat(r.get("ingresoNeto").decimalValue()).isEqualByComparingTo("0");
        assertThat(r.get("margenBruto")).isNull();
        assertThat(r.get("ventaPromedio")).isNull();
    }

    @Test
    void elReporteRespetaRolesYEmpresas() throws Exception {
        venderOk(usuarioToken, lineas(linea(camisa, 1)));
        getJson(REPORTE, usuarioToken).andExpect(status().isForbidden());
        getJson(REPORTE + "/mias", usuarioToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.ventas").value(1))
                .andExpect(jsonPath("$.unidades").value(1))
                .andExpect(jsonPath("$.total").value(11900))
                .andExpect(jsonPath("$.utilidad").doesNotExist());

        Empresa norte = crearEmpresa("Norte", "900000002-2");
        String adminNorte = crearUsuarioYEntrar("nora", Rol.ADMINISTRADOR_COMERCIAL, norte);
        getJson(REPORTE, adminNorte).andExpect(status().isOk()).andExpect(jsonPath("$.resumen.numeroVentas").value(0));
        // El SUPER_ADMIN lo consulta supervisando una empresa, nunca en modo global.
        String superAdmin = crearUsuarioYEntrar("root", Rol.SUPER_ADMIN, null);
        getEnEmpresa(REPORTE, superAdmin, empresa.getId()).andExpect(jsonPath("$.resumen.numeroVentas").value(1));
        getGlobal(REPORTE, superAdmin).andExpect(status().isBadRequest());
        // Fechas inválidas: 400.
        getJson(REPORTE + "?desde=2026-12-01&hasta=2026-11-01", adminToken).andExpect(status().isBadRequest());
        getJson(REPORTE + "?hasta=2999-01-01", adminToken).andExpect(status().isBadRequest());
    }

    @Test
    void elComprobanteRepresentaLaVentaYNoSePresentaComoFactura() throws Exception {
        politica("10", "30", "PERMITIR").andExpect(status().isOk());
        Map<String, Object> venta = ventaPagada(usuarioToken, lineas(linea(camisa, 2, "PORCENTAJE", "10"), linea(arroz, 1)),
                Map.of("clienteNombre", "Cliente de mostrador"));
        JsonNode vendida = leer(vender(usuarioToken, venta).andExpect(status().isCreated()));
        long id = vendida.get("id").asLong();

        byte[] pdf = getJson(VENTAS + "/" + id + "/comprobante", usuarioToken)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("V-00001.pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        String texto = texto(pdf);
        assertThat(texto).contains("Nexora", "NIT 900000001-1", "COMPROBANTE INTERNO DE VENTA", "V-00001",
                "Cliente de mostrador", "Producto CAM-01", "Producto ARR-01", "IVA 19 %", "Excluido",
                "$ 18.000", "$ 3.420", "$ 2.500", "TOTAL COP", "$ 23.920", "Efectivo")
                .contains("No es factura electrónica");
        assertThat(texto).doesNotContain("CUFE").doesNotContain("Resolución DIAN").doesNotContain("7.000");
        assertThat(vendida.get("total").decimalValue()).isEqualByComparingTo("23920");

        // Otro trabajador de la empresa no descarga comprobantes ajenos; otra empresa tampoco.
        String otroTrabajador = crearUsuarioYEntrar("caja2", Rol.TRABAJADOR, empresa);
        getJson(VENTAS + "/" + id + "/comprobante", otroTrabajador).andExpect(status().isNotFound());
        Empresa norte = crearEmpresa("Norte", "900000002-2");
        String adminNorte = crearUsuarioYEntrar("nora", Rol.ADMINISTRADOR_COMERCIAL, norte);
        getJson(VENTAS + "/" + id + "/comprobante", adminNorte).andExpect(status().isNotFound());
        getJson(VENTAS + "/" + id + "/comprobante", adminToken).andExpect(status().isOk());

        // Tras una anulación, el comprobante lo indica (y sigue mostrando los importes originales).
        postJson(VENTAS + "/" + id + "/anulacion", adminToken, json(Map.of("claveIdempotencia", clave(),
                "motivo", "Cliente se arrepintió"))).andExpect(status().isCreated());
        String anulado = texto(getJson(VENTAS + "/" + id + "/comprobante", adminToken).andReturn().getResponse()
                .getContentAsByteArray());
        assertThat(anulado).contains("VENTA ANULADA", "Cliente se arrepintió", "$ 23.920");
    }

    private static String texto(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            StringBuilder sb = new StringBuilder();
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int p = 1; p <= reader.getNumberOfPages(); p++) {
                sb.append(extractor.getTextFromPage(p)).append('\n');
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }
}
