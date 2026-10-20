package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.unisen.sgp.support.VentasApiTest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Devoluciones y anulaciones: trazables, sin borrar nada, sin devolver más de lo vendido (tampoco
 * con peticiones repetidas o simultáneas), con reingreso opcional al inventario e importes exactos.
 */
class DevolucionVentaTest extends VentasApiTest {

    private long camisa;
    private long venta;
    private long lineaCamisa;

    @BeforeEach
    void venderTresCamisas() throws Exception {
        long proveedor = crearProveedor("900111111-1", "Textiles del Valle");
        // 3 × $ 3.333,33 con IVA 19 %: importes con centavos para comprobar el reparto exacto.
        camisa = productoALaVenta(proveedor, "CAM-01", "3333.33", "GRAVADO", "19", 10, "2000");
        JsonNode vendida = venderOk(usuarioToken, lineas(linea(camisa, 3)));
        venta = vendida.get("id").asLong();
        lineaCamisa = vendida.get("lineas").get(0).get("id").asLong();
    }

    private int movimientos(String tipo) {
        return contar("SELECT COUNT(*) FROM movimientos_inventario WHERE tipo = ?", tipo);
    }

    @Test
    void devolucionesParcialesHastaLaTotalConImportesExactos() throws Exception {
        JsonNode primera = leer(devolver(venta, clave(), List.of(lineaDevuelta(lineaCamisa, 1, true)))
                .andExpect(status().isCreated()));
        assertThat(primera.get("estado").asText()).isEqualTo("DEVUELTA_PARCIALMENTE");
        assertThat(existencias(camisa)).isEqualTo(8);
        assertThat(movimientos("DEVOLUCION_VENTA")).isEqualTo(1);

        // La segunda no reingresa (dañada): se registra, pero el inventario no cambia.
        leer(devolver(venta, clave(), List.of(lineaDevuelta(lineaCamisa, 1, false))).andExpect(status().isCreated()));
        assertThat(existencias(camisa)).isEqualTo(8);
        assertThat(movimientos("DEVOLUCION_VENTA")).isEqualTo(1);

        JsonNode total = leer(devolver(venta, clave(), List.of(lineaDevuelta(lineaCamisa, 1, true)))
                .andExpect(status().isCreated()));
        assertThat(total.get("estado").asText()).isEqualTo("DEVUELTA");
        assertThat(existencias(camisa)).isEqualTo(9);
        // La suma de lo devuelto es exactamente lo cobrado, al centavo.
        assertThat(total.get("totalDevuelto").decimalValue()).isEqualByComparingTo(total.get("total").decimalValue());
        assertThat(total.get("devoluciones").size()).isEqualTo(3);
        assertThat(total.get("lineas").get(0).get("pendienteDeDevolver").asInt()).isZero();
        // El costo de lo devuelto vuelve al kárdex con el costo histórico de la venta.
        assertThat(jdbcTemplate.queryForObject("SELECT DISTINCT costo_unitario FROM movimientos_inventario WHERE tipo = "
                + "'DEVOLUCION_VENTA'", java.math.BigDecimal.class)).isEqualByComparingTo("2000");
        // Ya no se puede devolver más.
        devolver(venta, clave(), List.of(lineaDevuelta(lineaCamisa, 1, true))).andExpect(status().isConflict());
        // Nada se borró: la venta conserva sus líneas y su movimiento de salida.
        assertThat(contar("SELECT cantidad FROM detalles_venta WHERE venta_id = ?", venta)).isEqualTo(3);
        assertThat(movimientos("VENTA")).isEqualTo(1);
    }

    @Test
    void noSeDevuelveMasDeLoVendidoNiLineasAjenas() throws Exception {
        devolver(venta, clave(), List.of(lineaDevuelta(lineaCamisa, 4, true))).andExpect(status().isBadRequest());
        devolver(venta, clave(), List.of(lineaDevuelta(lineaCamisa, 0, true))).andExpect(status().isBadRequest());
        long otraLinea = venderOk(adminToken, lineas(linea(camisa, 1))).get("lineas").get(0).get("id").asLong();
        devolver(venta, clave(), List.of(lineaDevuelta(otraLinea, 1, true))).andExpect(status().isBadRequest());
        devolver(venta, clave(), List.of(lineaDevuelta(lineaCamisa, 1, true), lineaDevuelta(lineaCamisa, 1, true)))
                .andExpect(status().isBadRequest());
        assertThat(contar("SELECT COUNT(*) FROM devoluciones_venta")).isZero();
        assertThat(existencias(camisa)).isEqualTo(6);
    }

    @Test
    void lasDevolucionesRepetidasOSimultaneasNoDevuelvenDosVeces() throws Exception {
        String clave = clave();
        devolver(venta, clave, List.of(lineaDevuelta(lineaCamisa, 2, true))).andExpect(status().isCreated());
        devolver(venta, clave, List.of(lineaDevuelta(lineaCamisa, 2, true))).andExpect(status().isOk());
        assertThat(existencias(camisa)).isEqualTo(9);

        // Queda 1 por devolver: 4 peticiones simultáneas de 1 unidad con claves distintas.
        String cuerpo = "{\"claveIdempotencia\":\"%s\",\"motivo\":\"Simultánea\",\"lineas\":[{\"detalleVentaId\":"
                + lineaCamisa + ",\"cantidad\":1,\"reingresaInventario\":true}]}";
        List<Integer> estados = enParalelo(4, i -> () -> postJson(VENTAS + "/" + venta + "/devoluciones", adminToken,
                cuerpo.formatted(clave())));
        assertThat(Collections.frequency(estados, 201)).isEqualTo(1);
        assertThat(contar("SELECT cantidad_devuelta FROM detalles_venta WHERE id = ?", lineaCamisa)).isEqualTo(3);
        assertThat(existencias(camisa)).isEqualTo(10);
        assertThat(movimientos("DEVOLUCION_VENTA")).isEqualTo(2);
    }

    @Test
    void laAnulacionDelMismoDiaRevierteTodoYNoAdmiteDevoluciones() throws Exception {
        String clave = clave();
        JsonNode anulada = leer(postJson(VENTAS + "/" + venta + "/anulacion", adminToken,
                json(Map.of("claveIdempotencia", clave, "motivo", "Error de digitación"))).andExpect(status().isCreated()));
        assertThat(anulada.get("estado").asText()).isEqualTo("ANULADA");
        assertThat(anulada.get("anuladaPor").get("username").asText()).isEqualTo("admin");
        assertThat(anulada.get("motivoAnulacion").asText()).isEqualTo("Error de digitación");
        assertThat(existencias(camisa)).isEqualTo(10);
        // Repetir la petición no anula dos veces.
        postJson(VENTAS + "/" + venta + "/anulacion", adminToken,
                json(Map.of("claveIdempotencia", clave, "motivo", "Error de digitación"))).andExpect(status().isOk());
        assertThat(movimientos("DEVOLUCION_VENTA")).isEqualTo(1);
        postJson(VENTAS + "/" + venta + "/anulacion", adminToken,
                json(Map.of("claveIdempotencia", clave(), "motivo", "Otra vez"))).andExpect(status().isConflict());
        devolver(venta, clave(), List.of(lineaDevuelta(lineaCamisa, 1, true))).andExpect(status().isConflict());
    }

    @Test
    void noSeAnulaUnaVentaConDevolucionesNiDeOtroDia() throws Exception {
        devolver(venta, clave(), List.of(lineaDevuelta(lineaCamisa, 1, true))).andExpect(status().isCreated());
        postJson(VENTAS + "/" + venta + "/anulacion", adminToken, json(Map.of("claveIdempotencia", clave(),
                "motivo", "Tarde"))).andExpect(status().isConflict());

        long ayer = venderOk(usuarioToken, lineas(linea(camisa, 1))).get("id").asLong();
        jdbcTemplate.update("UPDATE ventas SET vendida_en = vendida_en - INTERVAL '2' DAY WHERE id = ?", ayer);
        postJson(VENTAS + "/" + ayer + "/anulacion", adminToken, json(Map.of("claveIdempotencia", clave(),
                "motivo", "Otro día"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("mismo día")));
    }

    @Test
    void soloElAdministradorComercialDevuelveOAnula() throws Exception {
        postJson(VENTAS + "/" + venta + "/devoluciones", usuarioToken, json(Map.of("claveIdempotencia", clave(),
                "motivo", "Yo", "lineas", List.of(lineaDevuelta(lineaCamisa, 1, true))))).andExpect(status().isForbidden());
        postJson(VENTAS + "/" + venta + "/anulacion", usuarioToken, json(Map.of("claveIdempotencia", clave(),
                "motivo", "Yo"))).andExpect(status().isForbidden());
        var norte = crearEmpresa("Norte", "900000002-2");
        String adminNorte = crearUsuarioYEntrar("nora", com.unisen.sgp.model.entity.Rol.ADMINISTRADOR_COMERCIAL, norte);
        postJson(VENTAS + "/" + venta + "/anulacion", adminNorte, json(Map.of("claveIdempotencia", clave(),
                "motivo", "Ajena"))).andExpect(status().isNotFound());
        assertThat(existencias(camisa)).isEqualTo(7);
    }
}
