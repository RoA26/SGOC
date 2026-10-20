package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.support.VentasApiTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Punto de venta: cálculo en el servidor, valores históricos inmutables, existencias con bloqueo,
 * descuentos y autorizaciones, idempotencia, concurrencia, pagos, visibilidad y aislamiento.
 */
class VentaPosTest extends VentasApiTest {

    private long proveedor;
    /** Gravado 19 %, $ 10.000 sin IVA, costo $ 7.000, 10 unidades. */
    private long camisa;
    /** Excluido, $ 2.500, costo $ 2.000, 5 unidades. */
    private long arroz;

    @BeforeEach
    void prepararCatalogo() throws Exception {
        proveedor = crearProveedor("900111111-1", "Textiles del Valle");
        camisa = productoALaVenta(proveedor, "CAM-01", "10000", "GRAVADO", "19", 10, "7000");
        arroz = productoALaVenta(proveedor, "ARR-01", "2500", "EXCLUIDO", "0", 5, "2000");
    }

    private int movimientosDeVenta() {
        return contar("SELECT COUNT(*) FROM movimientos_inventario WHERE tipo = 'VENTA'");
    }

    @Test
    void ventaValidaDescuentaExistenciasYGuardaLaCopiaHistorica() throws Exception {
        JsonNode venta = venderOk(usuarioToken, lineas(linea(camisa, 2), linea(arroz, 1)));
        assertThat(venta.get("numeroVisible").asText()).isEqualTo("V-00001");
        assertThat(venta.get("estado").asText()).isEqualTo("CONFIRMADA");
        assertThat(venta.get("baseGravable").decimalValue()).isEqualByComparingTo("22500");
        assertThat(venta.get("impuestos").decimalValue()).isEqualByComparingTo("3800");
        assertThat(venta.get("total").decimalValue()).isEqualByComparingTo("26300");
        assertThat(venta.get("vendidaPor").get("username").asText()).isEqualTo("compras");
        // El trabajador no recibe costos ni rentabilidad.
        assertThat(venta.get("rentabilidad")).isNull();
        assertThat(venta.get("lineas").get(0).get("costoUnitario")).isNull();
        assertThat(venta.toString()).doesNotContain("7000").doesNotContain("costo");

        assertThat(existencias(camisa)).isEqualTo(8);
        assertThat(existencias(arroz)).isEqualTo(4);
        assertThat(movimientosDeVenta()).isEqualTo(2);
        assertThat(contar("SELECT cantidad FROM movimientos_inventario WHERE tipo = 'VENTA' AND producto_id = ?", camisa))
                .isEqualTo(-2);

        // La gestión ve el costo histórico y la rentabilidad: 22.500 − (2 × 7.000 + 2.000) = 6.500.
        long id = venta.get("id").asLong();
        getJson(VENTAS + "/" + id, adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.rentabilidad.costo").value(16000))
                .andExpect(jsonPath("$.rentabilidad.utilidad").value(6500))
                .andExpect(jsonPath("$.rentabilidad.margen").value(28.89))
                .andExpect(jsonPath("$.lineas[0].costoUnitario").value(7000))
                .andExpect(jsonPath("$.lineas[0].metodoCosto").value("PROMEDIO_PONDERADO"))
                .andExpect(jsonPath("$.impuestosPorTarifa.length()").value(2));
    }

    @Test
    void preciosTotalesYEmpresaEnviadosPorElClienteSeIgnoran() throws Exception {
        Empresa otra = crearEmpresa("Norte", "900000002-2");
        Map<String, Object> venta = ventaPagada(usuarioToken, lineas(linea(camisa, 1)), Map.of());
        Map<String, Object> manipulada = new HashMap<>(venta);
        List<Map<String, Object>> lineasManipuladas = new ArrayList<>();
        Map<String, Object> l = new HashMap<>(linea(camisa, 1));
        l.put("precioUnitario", 1);
        l.put("tarifaIva", 0);
        l.put("costoUnitario", 1);
        lineasManipuladas.add(l);
        manipulada.put("lineas", lineasManipuladas);
        manipulada.put("total", 1);
        manipulada.put("empresaId", otra.getId());
        JsonNode respuesta = leer(vender(usuarioToken, manipulada).andExpect(status().isCreated()));
        assertThat(respuesta.get("total").decimalValue()).isEqualByComparingTo("11900");
        assertThat(contar("SELECT COUNT(*) FROM ventas WHERE empresa_id = ?", otra.getId())).isZero();

        // Pagar menos que el total calculado por el servidor no es posible.
        Map<String, Object> barata = ventaPagada(usuarioToken, lineas(linea(camisa, 1)), Map.of());
        barata.put("pagos", List.of(Map.of("medio", "EFECTIVO", "valor", 1)));
        vender(usuarioToken, barata).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.pagos").exists());
    }

    @Test
    void elPrecioElIvaYElCostoDeUnaVentaNoCambianAunqueCambieElCatalogo() throws Exception {
        long id = venderOk(usuarioToken, lineas(linea(camisa, 1))).get("id").asLong();

        condiciones(adminToken, camisa, "15000", "GRAVADO", "5", "Nuevo precio").andExpect(status().isOk());
        // Nueva entrada a otro costo: el promedio cambia (9 × 7.000 + 9 × 9.000) / 18 = 8.000.
        postJson(INVENTARIO + "/ajustes", adminToken, json(Map.of("productoId", camisa, "cantidad", 9,
                "motivo", "Compra local", "costoUnitario", 9000, "claveIdempotencia", clave()))).andExpect(status().isCreated());

        getJson(VENTAS + "/" + id, adminToken)
                .andExpect(jsonPath("$.lineas[0].precioUnitario").value(10000))
                .andExpect(jsonPath("$.lineas[0].tarifaIva").value(19))
                .andExpect(jsonPath("$.lineas[0].costoUnitario").value(7000))
                .andExpect(jsonPath("$.total").value(11900));
        JsonNode nueva = leer(getJson(VENTAS + "/" + venderOk(usuarioToken, lineas(linea(camisa, 1))).get("id").asLong(),
                adminToken));
        assertThat(nueva.get("lineas").get(0).get("precioUnitario").decimalValue()).isEqualByComparingTo("15000");
        assertThat(nueva.get("lineas").get(0).get("tarifaIva").decimalValue()).isEqualByComparingTo("5");
        assertThat(nueva.get("lineas").get(0).get("costoUnitario").decimalValue()).isEqualByComparingTo("8000");
        assertThat(nueva.get("total").decimalValue()).isEqualByComparingTo("15750");
    }

    @Test
    void existenciasInsuficientesNoRegistranNadaNiDescuentanOtrasLineas() throws Exception {
        Map<String, Object> venta = ventaPagada(usuarioToken, lineas(linea(camisa, 2), linea(arroz, 6)), Map.of());
        vender(usuarioToken, venta).andExpect(status().isConflict())
                .andExpect(jsonPath("$.faltantes.length()").value(1))
                .andExpect(jsonPath("$.faltantes[0].productoId").value(arroz))
                .andExpect(jsonPath("$.faltantes[0].disponibles").value(5))
                .andExpect(jsonPath("$.faltantes[0].solicitadas").value(6));
        assertThat(existencias(camisa)).isEqualTo(10);
        assertThat(existencias(arroz)).isEqualTo(5);
        assertThat(contar("SELECT COUNT(*) FROM ventas")).isZero();
        assertThat(movimientosDeVenta()).isZero();

        long agotado = productoALaVenta(proveedor, "AGO-01", "100", "EXCLUIDO", "0", 0, null);
        vender(usuarioToken, ventaPagada(usuarioToken, lineas(linea(agotado, 1)), Map.of())).andExpect(status().isConflict());
        // La cotización lo advierte antes de cobrar.
        postJson(VENTAS + "/cotizacion", usuarioToken, json(Map.of("lineas", lineas(linea(agotado, 1)))))
                .andExpect(jsonPath("$.existenciasSuficientes").value(false))
                .andExpect(jsonPath("$.lineas[0].disponibles").value(0));
    }

    @Test
    void productosAjenosSinPrecioRepetidosOCantidadesInvalidasSeRechazan() throws Exception {
        long sinPrecio = crearProducto("SINP-01", "Sin precio", "1000", proveedor);
        Empresa otra = crearEmpresa("Norte", "900000002-2");
        String adminOtra = crearUsuarioYEntrar("nora", Rol.ADMINISTRADOR_COMERCIAL, otra);

        vender(usuarioToken, Map.of("claveIdempotencia", clave(), "lineas", lineas(linea(sinPrecio, 1)),
                "pagos", List.of(Map.of("medio", "EFECTIVO", "valor", 1)))).andExpect(status().isBadRequest());
        // Un producto de otra empresa no existe para Norte.
        vender(adminOtra, Map.of("claveIdempotencia", clave(), "lineas", lineas(linea(camisa, 1)),
                "pagos", List.of(Map.of("medio", "EFECTIVO", "valor", 11900)))).andExpect(status().isBadRequest());
        vender(usuarioToken, Map.of("claveIdempotencia", clave(), "lineas", lineas(linea(camisa, 1), linea(camisa, 1)),
                "pagos", List.of(Map.of("medio", "EFECTIVO", "valor", 23800)))).andExpect(status().isBadRequest());
        vender(usuarioToken, Map.of("claveIdempotencia", clave(), "lineas", lineas(linea(camisa, 0)),
                "pagos", List.of(Map.of("medio", "EFECTIVO", "valor", 1)))).andExpect(status().isBadRequest());
        vender(usuarioToken, Map.of("claveIdempotencia", clave(), "lineas", lineas(linea(camisa, -3)),
                "pagos", List.of(Map.of("medio", "EFECTIVO", "valor", 1)))).andExpect(status().isBadRequest());
        assertThat(existencias(camisa)).isEqualTo(10);
        assertThat(contar("SELECT COUNT(*) FROM ventas")).isZero();
    }

    @Test
    void losDescuentosRespetanLosLimitesDeLaPolitica() throws Exception {
        // Sin política aprobada, el trabajador no puede descontar.
        vender(usuarioToken, ventaPagada(usuarioToken, lineas(linea(camisa, 1, "PORCENTAJE", "5")), Map.of()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.requiereAutorizacion").value(true));

        politica("10", "30", "PERMITIR").andExpect(status().isOk());
        JsonNode conDescuento = leer(vender(usuarioToken, ventaPagada(usuarioToken,
                lineas(linea(camisa, 1, "PORCENTAJE", "10")), Map.of())).andExpect(status().isCreated()));
        assertThat(conDescuento.get("descuentoLineas").decimalValue()).isEqualByComparingTo("1000");
        assertThat(conDescuento.get("total").decimalValue()).isEqualByComparingTo("10710");

        // 12 % por línea, o 10 % de línea + 1 % general (11 % efectivo): superan el límite.
        vender(usuarioToken, ventaPagada(usuarioToken, lineas(linea(camisa, 1, "PORCENTAJE", "12")), Map.of()))
                .andExpect(status().isForbidden());
        vender(usuarioToken, ventaPagada(usuarioToken, lineas(linea(camisa, 1, "PORCENTAJE", "10")),
                Map.of("descuentoGeneral", Map.of("tipo", "PORCENTAJE", "valor", 1)))).andExpect(status().isForbidden());
        // Valor fijo equivalente al 10 %: permitido; 1.001 pesos (10,01 %): no.
        vender(usuarioToken, ventaPagada(usuarioToken, lineas(linea(camisa, 1, "VALOR", "1000")), Map.of()))
                .andExpect(status().isCreated());
        vender(usuarioToken, ventaPagada(usuarioToken, lineas(linea(camisa, 1, "VALOR", "1001")), Map.of()))
                .andExpect(status().isForbidden());
        // Valores imposibles: 400.
        vender(usuarioToken, Map.of("claveIdempotencia", clave(), "lineas", lineas(linea(camisa, 1)),
                "descuentoGeneral", Map.of("tipo", "PORCENTAJE", "valor", -5),
                "pagos", List.of(Map.of("medio", "EFECTIVO", "valor", 11900)))).andExpect(status().isBadRequest());
        postJson(VENTAS + "/cotizacion", usuarioToken, json(Map.of("lineas", lineas(linea(camisa, 1, "PORCENTAJE", "150")))))
                .andExpect(status().isBadRequest());
        // El administrador comercial descuenta hasta su propio límite (30 %).
        vender(adminToken, ventaPagada(adminToken, lineas(linea(camisa, 1, "PORCENTAJE", "25")), Map.of()))
                .andExpect(status().isCreated());
        vender(adminToken, ventaPagada(adminToken, lineas(linea(camisa, 1, "PORCENTAJE", "31")), Map.of()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unaAutorizacionDeUnSoloUsoPermiteSuperarElLimite() throws Exception {
        politica("10", "30", "PERMITIR").andExpect(status().isOk());
        String codigo = leer(postJson(COMERCIAL + "/autorizaciones", adminToken, json(Map.of("descuentoMaximo", 20,
                "permiteBajoCosto", false, "motivo", "Cliente frecuente")))).get("codigo").asText();

        // Cubre hasta el 20 %: con 25 % no sirve.
        vender(usuarioToken, ventaPagada(usuarioToken, lineas(linea(camisa, 1, "PORCENTAJE", "25")),
                Map.of("codigoAutorizacion", codigo))).andExpect(status().isForbidden());
        // La cotización indica si el código cubre la venta (el vendedor puede teclearlo en minúsculas).
        postJson(VENTAS + "/cotizacion", usuarioToken, json(Map.of("lineas", lineas(linea(camisa, 1, "PORCENTAJE", "15")),
                "codigoAutorizacion", codigo.toLowerCase().replace("-", ""))))
                .andExpect(jsonPath("$.requiereAutorizacion").value(true))
                .andExpect(jsonPath("$.autorizacionValida").value(true));
        JsonNode venta = leer(vender(usuarioToken, ventaPagada(usuarioToken, lineas(linea(camisa, 1, "PORCENTAJE", "15")),
                Map.of("codigoAutorizacion", codigo))).andExpect(status().isCreated()));
        assertThat(venta.get("autorizacion").get("autorizadaPor").get("username").asText()).isEqualTo("admin");
        assertThat(venta.get("autorizacion").get("descuentoMaximo").decimalValue()).isEqualByComparingTo("20");
        assertThat(contar("SELECT COUNT(*) FROM autorizaciones_comerciales WHERE venta_id = ?",
                venta.get("id").asLong())).isEqualTo(1);

        // Ya usada: no sirve para otra venta.
        vender(usuarioToken, ventaPagada(usuarioToken, lineas(linea(camisa, 1, "PORCENTAJE", "15")),
                Map.of("codigoAutorizacion", codigo))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ya se usó")));
        // Vencida: tampoco.
        String vencida = leer(postJson(COMERCIAL + "/autorizaciones", adminToken, json(Map.of("descuentoMaximo", 20,
                "permiteBajoCosto", false, "motivo", "Vence")))).get("codigo").asText();
        jdbcTemplate.update("UPDATE autorizaciones_comerciales SET creada_en = creada_en - INTERVAL '2' HOUR, "
                + "expira_en = creada_en - INTERVAL '1' HOUR WHERE codigo = ?", vencida);
        vender(usuarioToken, ventaPagada(usuarioToken, lineas(linea(camisa, 1, "PORCENTAJE", "15")),
                Map.of("codigoAutorizacion", vencida))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("venció")));
    }

    @Test
    void laVentaBajoElCostoSeAdviertePorDefectoYPuedeExigirAutorizacion() throws Exception {
        long liquidacion = productoALaVenta(proveedor, "LIQ-01", "500", "EXCLUIDO", "0", 10, "750");
        JsonNode permitida = leer(vender(usuarioToken, ventaPagada(usuarioToken, lineas(linea(liquidacion, 1)), Map.of()))
                .andExpect(status().isCreated()));
        getJson(VENTAS + "/" + permitida.get("id").asLong(), adminToken)
                .andExpect(jsonPath("$.lineas[0].bajoCosto").value(true))
                .andExpect(jsonPath("$.rentabilidad.utilidad").value(-250));

        politica("0", "100", "AUTORIZACION").andExpect(status().isOk());
        vender(usuarioToken, ventaPagada(usuarioToken, lineas(linea(liquidacion, 1)), Map.of())).andExpect(status().isForbidden());
        String sinBajoCosto = leer(postJson(COMERCIAL + "/autorizaciones", adminToken, json(Map.of("descuentoMaximo", 0,
                "permiteBajoCosto", false, "motivo", "Solo descuento")))).get("codigo").asText();
        vender(usuarioToken, ventaPagada(usuarioToken, lineas(linea(liquidacion, 1)), Map.of("codigoAutorizacion", sinBajoCosto)))
                .andExpect(status().isForbidden());
        String conBajoCosto = leer(postJson(COMERCIAL + "/autorizaciones", adminToken, json(Map.of("descuentoMaximo", 0,
                "permiteBajoCosto", true, "motivo", "Liquidación de temporada")))).get("codigo").asText();
        vender(usuarioToken, ventaPagada(usuarioToken, lineas(linea(liquidacion, 1)), Map.of("codigoAutorizacion", conBajoCosto)))
                .andExpect(status().isCreated());
    }

    @Test
    void reintentarConLaMismaClaveNoDuplicaLaVenta() throws Exception {
        Map<String, Object> venta = ventaPagada(usuarioToken, lineas(linea(camisa, 2)), Map.of());
        long id = leer(vender(usuarioToken, venta).andExpect(status().isCreated())).get("id").asLong();
        long repetida = leer(vender(usuarioToken, venta).andExpect(status().isOk())).get("id").asLong();
        assertThat(repetida).isEqualTo(id);
        assertThat(existencias(camisa)).isEqualTo(8);
        assertThat(movimientosDeVenta()).isEqualTo(1);

        Map<String, Object> distinta = new HashMap<>(venta);
        distinta.put("lineas", lineas(linea(camisa, 3)));
        vender(usuarioToken, distinta).andExpect(status().isConflict());
        assertThat(contar("SELECT COUNT(*) FROM ventas")).isEqualTo(1);
    }

    @Test
    void laMismaClaveEnviadaAVecesALaVezRegistraUnaSolaVenta() throws Exception {
        Map<String, Object> venta = ventaPagada(usuarioToken, lineas(linea(camisa, 3)), Map.of());
        String cuerpo = json(venta);
        List<Integer> estados = enParalelo(4, i -> () -> postJson(VENTAS, usuarioToken, cuerpo));
        assertThat(estados).containsOnly(201, 200);
        assertThat(Collections.frequency(estados, 201)).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM ventas")).isEqualTo(1);
        assertThat(existencias(camisa)).isEqualTo(7);
        assertThat(movimientosDeVenta()).isEqualTo(1);
    }

    @Test
    void dosCajasNoVendenLasMismasUnidades() throws Exception {
        // 10 unidades y 5 ventas simultáneas de 3: solo 3 caben (9 unidades); nunca saldo negativo.
        List<String> cuerpos = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            cuerpos.add(json(ventaPagada(usuarioToken, lineas(linea(camisa, 3)), Map.of())));
        }
        String otroToken = crearUsuarioYEntrar("caja2", Rol.TRABAJADOR, empresa);
        List<Integer> estados = enParalelo(5, i -> () -> postJson(VENTAS, i % 2 == 0 ? usuarioToken : otroToken,
                cuerpos.get(i)));
        assertThat(Collections.frequency(estados, 201)).isEqualTo(3);
        assertThat(Collections.frequency(estados, 409)).isEqualTo(2);
        assertThat(existencias(camisa)).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM ventas")).isEqualTo(3);
        assertThat(movimientosDeVenta()).isEqualTo(3);
        // Numeración correlativa sin huecos ni repetidos.
        assertThat(jdbcTemplate.queryForList("SELECT numero FROM ventas ORDER BY numero", Long.class))
                .containsExactly(1L, 2L, 3L);
        // Saldos del kárdex coherentes: 7, 4, 1.
        assertThat(jdbcTemplate.queryForList("SELECT saldo_resultante FROM movimientos_inventario WHERE tipo = 'VENTA' "
                + "ORDER BY id", Integer.class)).containsExactly(7, 4, 1);
    }

    @Test
    void pagosMixtosYCambioEnEfectivo() throws Exception {
        Map<String, Object> venta = ventaPagada(usuarioToken, lineas(linea(camisa, 1)), Map.of());
        venta.put("pagos", List.of(Map.of("medio", "TARJETA_DEBITO", "valor", 5000, "referencia", "AUT-123"),
                Map.of("medio", "EFECTIVO", "valor", 6900, "recibido", 10000)));
        leer(vender(usuarioToken, venta).andExpect(status().isCreated()));
        assertThat(jdbcTemplate.queryForObject("SELECT cambio FROM pagos_venta WHERE medio = 'EFECTIVO'", BigDecimal.class))
                .isEqualByComparingTo("3100");

        Map<String, Object> mal = ventaPagada(usuarioToken, lineas(linea(camisa, 1)), Map.of());
        mal.put("pagos", List.of(Map.of("medio", "TARJETA_CREDITO", "valor", 11900, "recibido", 20000)));
        vender(usuarioToken, mal).andExpect(status().isBadRequest());
        mal.put("pagos", List.of(Map.of("medio", "EFECTIVO", "valor", 11900, "recibido", 10000)));
        vender(usuarioToken, mal).andExpect(status().isBadRequest());
    }

    @Test
    void elTrabajadorSoloVeSusVentasYElSuperAdminNoVende() throws Exception {
        long delTrabajador = venderOk(usuarioToken, lineas(linea(arroz, 1))).get("id").asLong();
        long delAdmin = venderOk(adminToken, lineas(linea(arroz, 1))).get("id").asLong();
        getJson(VENTAS, usuarioToken).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(delTrabajador));
        getJson(VENTAS + "?vendedorId=0", usuarioToken).andExpect(jsonPath("$.content.length()").value(1));
        getJson(VENTAS + "/" + delAdmin, usuarioToken).andExpect(status().isNotFound());
        getJson(VENTAS, adminToken).andExpect(jsonPath("$.content.length()").value(2));

        String superAdmin = crearUsuarioYEntrar("root", Rol.SUPER_ADMIN, null);
        postEnEmpresa(VENTAS, superAdmin, empresa.getId(), json(ventaPagada(adminToken, lineas(linea(arroz, 1)), Map.of())))
                .andExpect(status().isForbidden());
        getEnEmpresa(VENTAS + "/" + delAdmin, superAdmin, empresa.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.rentabilidad").exists());
        getGlobal(VENTAS, superAdmin).andExpect(status().isBadRequest());

        Empresa norte = crearEmpresa("Norte", "900000002-2");
        String adminNorte = crearUsuarioYEntrar("nora", Rol.ADMINISTRADOR_COMERCIAL, norte);
        getJson(VENTAS + "/" + delAdmin, adminNorte).andExpect(status().isNotFound());
        getJson(VENTAS, adminNorte).andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void laCotizacionCoincideConLaVentaConfirmada() throws Exception {
        politica("10", "30", "PERMITIR").andExpect(status().isOk());
        List<Map<String, Object>> carrito = lineas(linea(camisa, 3, "PORCENTAJE", "5"), linea(arroz, 2));
        Map<String, Object> general = Map.of("tipo", "VALOR", "valor", 777);
        JsonNode cotizacion = leer(postJson(VENTAS + "/cotizacion", usuarioToken,
                json(Map.of("lineas", carrito, "descuentoGeneral", general))).andExpect(status().isOk()));
        JsonNode venta = leer(vender(usuarioToken, ventaPagada(usuarioToken, carrito, Map.of("descuentoGeneral", general)))
                .andExpect(status().isCreated()));
        for (String campo : List.of("subtotal", "descuentoLineas", "descuentoGeneral", "baseGravable", "impuestos", "total")) {
            assertThat(venta.get(campo).decimalValue()).as(campo).isEqualByComparingTo(cotizacion.get(campo).decimalValue());
        }
        // Las líneas suman exactamente el total de la venta.
        BigDecimal suma = BigDecimal.ZERO;
        for (JsonNode l : venta.get("lineas")) {
            suma = suma.add(l.get("total").decimalValue());
        }
        assertThat(suma).isEqualByComparingTo(venta.get("total").decimalValue());
    }
}
