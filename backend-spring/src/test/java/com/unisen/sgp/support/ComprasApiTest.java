package com.unisen.sgp.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.unisen.sgp.tenant.TenantFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base de los tests de compras e inventario: ayudas para crear órdenes, emitirlas, recibirlas y
 * ajustar existencias a través de la API real (con la cadena de seguridad completa).
 */
public abstract class ComprasApiTest extends ApiIntegrationTest {

    protected static final String ORDENES = "/api/v1/ordenes-compra";
    protected static final String INVENTARIO = "/api/v1/inventario";

    /** Crea un borrador con una línea por producto ({@code [productoId, cantidad]}) y devuelve la respuesta. */
    protected JsonNode crearOrden(String token, long proveedorId, long[]... lineas) throws Exception {
        List<Map<String, Object>> detalles = new ArrayList<>();
        for (long[] linea : lineas) {
            detalles.add(Map.of("productoId", linea[0], "cantidad", linea[1]));
        }
        return leer(postJson(ORDENES, token, json(Map.of("proveedorId", proveedorId, "detalles", detalles))));
    }

    protected ResultActions emitir(long ordenId, String token) throws Exception {
        return postJson(ORDENES + "/" + ordenId + "/emitir", token, "");
    }

    protected ResultActions cancelar(long ordenId, String token, String motivo) throws Exception {
        return postJson(ORDENES + "/" + ordenId + "/cancelar", token, json(Map.of("motivo", motivo)));
    }

    /** Recepción con una clave nueva; {@code lineas} son pares {@code [detalleOrdenId, cantidad]}. */
    protected ResultActions recibir(long ordenId, String token, long[]... lineas) throws Exception {
        return recibirConClave(ordenId, token, UUID.randomUUID().toString(), lineas);
    }

    protected ResultActions recibirConClave(long ordenId, String token, String clave, long[]... lineas)
            throws Exception {
        List<Map<String, Object>> detalles = new ArrayList<>();
        for (long[] linea : lineas) {
            detalles.add(Map.of("detalleOrdenId", linea[0], "cantidad", linea[1]));
        }
        return postJson(ORDENES + "/" + ordenId + "/recepciones", token,
                json(Map.of("claveIdempotencia", clave, "detalles", detalles)));
    }

    /**
     * Programa la entrega con una fecha relativa a hoy (zona del negocio), sin franja salvo que se
     * indique. {@code extra} añade o sustituye campos (horaDesde, motivo, confirmadaProveedor…).
     */
    protected ResultActions programar(long ordenId, String token, int diasDesdeHoy, Map<String, Object> extra)
            throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("fechaEstimada", hoy().plusDays(diasDesdeHoy).toString());
        body.putAll(extra);
        return putJson(ORDENES + "/" + ordenId + "/entrega", token, json(body));
    }

    /** Hoy en la zona del negocio (America/Bogota), como el backend. */
    protected static java.time.LocalDate hoy() {
        return java.time.LocalDate.now(java.time.ZoneId.of("America/Bogota"));
    }

    /**
     * Simula el paso del tiempo: mueve la fecha estimada vigente de la orden (la API no admite
     * programar en el pasado). SQL directo sobre el historial, solo en tests.
     */
    protected void moverFechaEstimada(long ordenId, int diasDesdeHoy) {
        jdbcTemplate.update("UPDATE programaciones_entrega SET fecha_estimada = ? WHERE id = "
                + "(SELECT programacion_entrega_id FROM ordenes_compra WHERE id = ?)",
                java.sql.Date.valueOf(hoy().plusDays(diasDesdeHoy)), ordenId);
    }

    /** Recepción con líneas ({@code [detalleOrdenId, cantidad]}) e incidencias como mapas. */
    protected ResultActions recibirConIncidencias(long ordenId, String token, String clave, List<long[]> lineas,
                                                  List<Map<String, Object>> incidencias) throws Exception {
        List<Map<String, Object>> detalles = new ArrayList<>();
        for (long[] linea : lineas) {
            detalles.add(Map.of("detalleOrdenId", linea[0], "cantidad", linea[1]));
        }
        return postJson(ORDENES + "/" + ordenId + "/recepciones", token,
                json(Map.of("claveIdempotencia", clave, "detalles", detalles, "incidencias", incidencias)));
    }

    protected ResultActions ajustar(String token, long productoId, int cantidad, String motivo) throws Exception {
        return ajustarConClave(token, productoId, cantidad, motivo, UUID.randomUUID().toString());
    }

    protected ResultActions ajustarConClave(String token, long productoId, int cantidad, String motivo, String clave)
            throws Exception {
        return postJson(INVENTARIO + "/ajustes", token, json(Map.of("productoId", productoId, "cantidad", cantidad,
                "motivo", motivo, "claveIdempotencia", clave)));
    }

    /** Orden emitida de {@code cantidad} unidades de un producto; devuelve la respuesta de la emisión. */
    protected JsonNode ordenEmitida(long proveedorId, long productoId, long cantidad) throws Exception {
        long ordenId = crearOrden(adminToken, proveedorId, new long[] {productoId, cantidad}).get("id").asLong();
        return leer(emitir(ordenId, adminToken));
    }

    /** Existencias actuales del producto, leídas en la BD. */
    protected int existencias(long productoId) {
        return jdbcTemplate.queryForObject("SELECT cantidad FROM existencias WHERE producto_id = ?", Integer.class,
                productoId);
    }

    protected int contar(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    /** GET del SUPER_ADMIN supervisando una empresa ({@code X-Tenant-ID: <id>}). */
    protected ResultActions getEnEmpresa(String url, String token, long empresaId) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(TenantFilter.HEADER_TENANT, Long.toString(empresaId)));
    }

    protected ResultActions postEnEmpresa(String url, String token, long empresaId, String body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(TenantFilter.HEADER_TENANT, Long.toString(empresaId))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /**
     * Lanza {@code n} peticiones a la vez (todas esperan la misma señal de salida) y devuelve sus
     * códigos HTTP en el orden de lanzamiento.
     */
    protected List<Integer> enParalelo(int n, IntFunction<ResultActionsSupplier> peticion) throws Exception {
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(n);
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                ResultActionsSupplier accion = peticion.apply(i);
                Callable<Integer> tarea = () -> {
                    salida.await();
                    return accion.get().andReturn().getResponse().getStatus();
                };
                futuros.add(executor.submit(tarea));
            }
            salida.countDown();
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> futuro : futuros) {
                estados.add(futuro.get(60, TimeUnit.SECONDS));
            }
            return estados;
        } finally {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    protected interface ResultActionsSupplier {
        ResultActions get() throws Exception;
    }
}
