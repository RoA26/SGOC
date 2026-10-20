package com.unisen.sgp.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base de los tests del módulo comercial: productos a la venta con existencias y costo, ventas,
 * devoluciones y política, siempre a través de la API real (con la cadena de seguridad completa).
 */
public abstract class VentasApiTest extends ComprasApiTest {

    protected static final String COMERCIAL = "/api/v1/comercial";
    protected static final String VENTAS = "/api/v1/ventas";

    /**
     * Producto con precio de venta (sin IVA), tratamiento del IVA y {@code unidades} en
     * existencias con costo {@code costo} (ajuste de inventario inicial). Devuelve su id.
     */
    protected long productoALaVenta(long proveedorId, String sku, String precioVenta, String tratamiento, String tarifa,
                                    int unidades, String costo) throws Exception {
        long id = crearProducto(sku, "Producto " + sku, "1000", proveedorId);
        condiciones(adminToken, id, precioVenta, tratamiento, tarifa, "Precio inicial").andExpect(status().isOk());
        if (unidades > 0) {
            Map<String, Object> ajuste = new HashMap<>(Map.of("productoId", id, "cantidad", unidades,
                    "motivo", "Inventario inicial", "claveIdempotencia", UUID.randomUUID().toString()));
            if (costo != null) {
                ajuste.put("costoUnitario", new BigDecimal(costo));
            }
            postJson(INVENTARIO + "/ajustes", adminToken, json(ajuste)).andExpect(status().isCreated());
        }
        return id;
    }

    protected ResultActions condiciones(String token, long productoId, String precio, String tratamiento, String tarifa,
                                        String motivo) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("precioVenta", new BigDecimal(precio), "tratamientoIva", tratamiento,
                "tarifaIva", new BigDecimal(tarifa)));
        if (motivo != null) {
            body.put("motivo", motivo);
        }
        return putJson(COMERCIAL + "/productos/" + productoId + "/condiciones", token, json(body));
    }

    protected ResultActions politica(String trabajador, String administrador, String bajoCosto) throws Exception {
        return putJson(COMERCIAL + "/politica", adminToken, json(Map.of("descuentoMaxTrabajador", new BigDecimal(trabajador),
                "descuentoMaxAdministrador", new BigDecimal(administrador), "ventaBajoCosto", bajoCosto)));
    }

    /** Línea {@code productoId × cantidad}, con descuento opcional. */
    protected static Map<String, Object> linea(long productoId, int cantidad) {
        return new HashMap<>(Map.of("productoId", productoId, "cantidad", cantidad));
    }

    protected static Map<String, Object> linea(long productoId, int cantidad, String tipo, String valor) {
        Map<String, Object> linea = linea(productoId, cantidad);
        linea.put("descuento", Map.of("tipo", tipo, "valor", new BigDecimal(valor)));
        return linea;
    }

    /** Cotiza para conocer el total y paga ese total en efectivo. */
    protected Map<String, Object> ventaPagada(String token, List<Map<String, Object>> lineas, Map<String, Object> extra)
            throws Exception {
        Map<String, Object> cotizacion = new HashMap<>(Map.of("lineas", lineas));
        if (extra.containsKey("descuentoGeneral")) {
            cotizacion.put("descuentoGeneral", extra.get("descuentoGeneral"));
        }
        JsonNode cotizada = leer(postJson(VENTAS + "/cotizacion", token, json(cotizacion)).andExpect(status().isOk()));
        Map<String, Object> venta = new HashMap<>(Map.of("claveIdempotencia", UUID.randomUUID().toString(),
                "lineas", lineas,
                "pagos", List.of(Map.of("medio", "EFECTIVO", "valor", cotizada.get("total").decimalValue()))));
        venta.putAll(extra);
        return venta;
    }

    protected ResultActions vender(String token, Map<String, Object> venta) throws Exception {
        return postJson(VENTAS, token, json(venta));
    }

    /** Vende y devuelve la respuesta (201). */
    protected JsonNode venderOk(String token, List<Map<String, Object>> lineas) throws Exception {
        return leer(vender(token, ventaPagada(token, lineas, Map.of())).andExpect(status().isCreated()));
    }

    protected ResultActions devolver(long ventaId, String clave, List<Map<String, Object>> lineas) throws Exception {
        return postJson(VENTAS + "/" + ventaId + "/devoluciones", adminToken,
                json(Map.of("claveIdempotencia", clave, "motivo", "El cliente devuelve", "lineas", lineas)));
    }

    protected static Map<String, Object> lineaDevuelta(long detalleVentaId, int cantidad, boolean reingresa) {
        return Map.of("detalleVentaId", detalleVentaId, "cantidad", cantidad, "reingresaInventario", reingresa);
    }

    @SafeVarargs
    protected static List<Map<String, Object>> lineas(Map<String, Object>... lineas) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Map<String, Object> linea : lineas) {
            lista.add(linea);
        }
        return lista;
    }

    protected static String clave() {
        return UUID.randomUUID().toString();
    }
}
