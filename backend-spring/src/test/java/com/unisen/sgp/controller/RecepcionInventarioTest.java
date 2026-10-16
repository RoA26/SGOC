package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.support.ComprasApiTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Recepciones, existencias y kárdex (Entrega C): recepciones parciales y totales, integridad
 * transaccional, idempotencia, concurrencia (nunca se recibe de más ni quedan existencias
 * negativas), ajustes, permisos y aislamiento entre empresas.
 */
class RecepcionInventarioTest extends ComprasApiTest {

    private long proveedor;
    private long tornillo;
    private long tuerca;

    @BeforeEach
    void prepararCatalogo() throws Exception {
        proveedor = crearProveedor("900111111-1", "Aceros Andinos");
        tornillo = crearProducto("TOR-01", "Tornillo", "1000", proveedor);
        tuerca = crearProducto("TUE-01", "Tuerca", "250", proveedor);
    }

    // ---------------------------------------------------------------- recepciones

    @Test
    void recepcionesParcialesHastaCompletarLaOrdenConKardexTrazable() throws Exception {
        JsonNode orden = leer(emitir(crearOrden(adminToken, proveedor, new long[] {tornillo, 10}, new long[] {tuerca, 4})
                .get("id").asLong(), adminToken));
        long id = orden.get("id").asLong();
        long lineaTornillo = orden.at("/detalles/0/id").asLong();
        long lineaTuerca = orden.at("/detalles/1/id").asLong();

        JsonNode primera = leer(recibir(id, adminToken, new long[] {lineaTornillo, 6})
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ordenNumero").value(1))
                .andExpect(jsonPath("$.recibidaPor.username").value("admin"))
                .andExpect(jsonPath("$.unidades").value(6)));
        assertThat(primera.get("valor").decimalValue()).isEqualByComparingTo("6000");
        getJson(ORDENES + "/" + id, adminToken)
                .andExpect(jsonPath("$.estado").value("RECIBIDA_PARCIALMENTE"))
                .andExpect(jsonPath("$.detalles[0].cantidadRecibida").value(6))
                .andExpect(jsonPath("$.detalles[0].pendiente").value(4))
                .andExpect(jsonPath("$.unidadesRecibidas").value(6));
        assertThat(existencias(tornillo)).isEqualTo(6);

        recibir(id, adminToken, new long[] {lineaTornillo, 4}, new long[] {lineaTuerca, 4}).andExpect(status().isCreated());
        getJson(ORDENES + "/" + id, adminToken).andExpect(jsonPath("$.estado").value("RECIBIDA"));
        assertThat(existencias(tornillo)).isEqualTo(10);
        assertThat(existencias(tuerca)).isEqualTo(4);
        // Una orden recibida ya no admite más mercancía.
        recibir(id, adminToken, new long[] {lineaTornillo, 1}).andExpect(status().isConflict());

        // Historial de la orden y kárdex: un movimiento por línea recibida, con saldo y costo de la orden.
        getJson(ORDENES + "/" + id + "/recepciones", adminToken).andExpect(jsonPath("$.length()").value(2));
        JsonNode kardex = leer(getJson(INVENTARIO + "/movimientos?productoId=" + tornillo, adminToken).andExpect(status().isOk()));
        assertThat(kardex.at("/page/totalElements").asInt()).isEqualTo(2);
        JsonNode ultimo = kardex.at("/content/0");
        assertThat(ultimo.get("tipo").asText()).isEqualTo("RECEPCION");
        assertThat(ultimo.get("cantidad").asInt()).isEqualTo(4);
        assertThat(ultimo.get("saldoResultante").asInt()).isEqualTo(10);
        assertThat(ultimo.get("costoUnitario").decimalValue()).isEqualByComparingTo("1000");
        assertThat(ultimo.get("ordenNumero").asLong()).isEqualTo(1);
        assertThat(ultimo.get("usuario").get("username").asText()).isEqualTo("admin");
        // El saldo es siempre la suma de los movimientos.
        assertThat(contar("SELECT SUM(cantidad) FROM movimientos_inventario WHERE producto_id = ?", tornillo))
                .isEqualTo(existencias(tornillo));
        getJson("/api/v1/recepciones", adminToken).andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].proveedor").value("Aceros Andinos"));
    }

    @Test
    void rechazaRecibirDeMasLineasAjenasYOrdenesQueNoAdmitenRecepcion() throws Exception {
        JsonNode orden = ordenEmitida(proveedor, tornillo, 5);
        long id = orden.get("id").asLong();
        long linea = orden.at("/detalles/0/id").asLong();
        JsonNode otra = ordenEmitida(proveedor, tuerca, 5);
        long lineaDeOtra = otra.at("/detalles/0/id").asLong();

        recibir(id, adminToken, new long[] {linea, 6})
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['detalles[0].cantidad']").value("Solo quedan 5 unidades pendientes de «Tornillo»."));
        recibir(id, adminToken, new long[] {lineaDeOtra, 1})
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['detalles[0].detalleOrdenId']").value("La línea no pertenece a esta orden."));
        recibir(id, adminToken, new long[] {linea, 1}, new long[] {linea, 1})
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['detalles[1].detalleOrdenId']").exists());
        recibir(id, adminToken, new long[] {linea, 0}).andExpect(status().isBadRequest());
        postJson(ORDENES + "/" + id + "/recepciones", adminToken, json(Map.of("claveIdempotencia", "corta",
                "detalles", List.of(Map.of("detalleOrdenId", linea, "cantidad", 1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.claveIdempotencia").exists());

        JsonNode borrador = crearOrden(adminToken, proveedor, new long[] {tornillo, 5});
        recibir(borrador.get("id").asLong(), adminToken, new long[] {borrador.at("/detalles/0/id").asLong(), 1})
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Emite la orden")));
        cancelar(otra.get("id").asLong(), adminToken, "Duplicada").andExpect(status().isOk());
        recibir(otra.get("id").asLong(), adminToken, new long[] {lineaDeOtra, 1}).andExpect(status().isConflict());

        // Ningún intento fallido dejó rastro: todo o nada.
        assertThat(contar("SELECT COUNT(*) FROM recepciones")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM movimientos_inventario")).isZero();
        assertThat(existencias(tornillo)).isZero();
        getJson(ORDENES + "/" + id, adminToken).andExpect(jsonPath("$.estado").value("EMITIDA"));
    }

    @Test
    void repetirUnaRecepcionConLaMismaClaveNoDuplicaLaMercancia() throws Exception {
        JsonNode orden = ordenEmitida(proveedor, tornillo, 10);
        long id = orden.get("id").asLong();
        long linea = orden.at("/detalles/0/id").asLong();
        String clave = UUID.randomUUID().toString();

        long recepcion = leer(recibirConClave(id, adminToken, clave, new long[] {linea, 3}).andExpect(status().isCreated()))
                .get("id").asLong();
        // Reintento idéntico (p. ej. tras un corte de red): 200 con la misma recepción.
        recibirConClave(id, adminToken, clave, new long[] {linea, 3})
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(recepcion));
        assertThat(existencias(tornillo)).isEqualTo(3);
        // Misma clave con otros datos u otra orden: 409, sin efectos.
        recibirConClave(id, adminToken, clave, new long[] {linea, 4}).andExpect(status().isConflict());
        JsonNode otra = ordenEmitida(proveedor, tuerca, 2);
        recibirConClave(otra.get("id").asLong(), adminToken, clave, new long[] {otra.at("/detalles/0/id").asLong(), 2})
                .andExpect(status().isConflict());
        assertThat(contar("SELECT COUNT(*) FROM recepciones")).isEqualTo(1);
        assertThat(existencias(tornillo)).isEqualTo(3);
        assertThat(existencias(tuerca)).isZero();
    }

    @Test
    void recepcionesSimultaneasNuncaRecibenMasDeLoPedido() throws Exception {
        for (int ronda = 0; ronda < 3; ronda++) {
            JsonNode orden = ordenEmitida(proveedor, tornillo, 5);
            long id = orden.get("id").asLong();
            long linea = orden.at("/detalles/0/id").asLong();
            int antes = existencias(tornillo);
            // Cuatro recepciones de 3 unidades a la vez sobre 5 pendientes: solo cabe una.
            List<Integer> estados = enParalelo(4, i -> () -> recibir(id, adminToken, new long[] {linea, 3}));
            assertThat(estados).as("ronda %d", ronda).containsOnlyOnce(201);
            assertThat(estados).as("ronda %d", ronda).filteredOn(e -> e != 201).allMatch(e -> e == 400 || e == 409);
            assertThat(existencias(tornillo) - antes).isEqualTo(3);
            assertThat(contar("SELECT cantidad_recibida FROM detalles_orden_compra WHERE id = ?", linea)).isEqualTo(3);
        }
    }

    @Test
    void laMismaClaveEnviadaAVecesALaVezSeRegistraUnaSolaVez() throws Exception {
        JsonNode orden = ordenEmitida(proveedor, tornillo, 10);
        long id = orden.get("id").asLong();
        long linea = orden.at("/detalles/0/id").asLong();
        String clave = UUID.randomUUID().toString();
        List<Integer> estados = enParalelo(4, i -> () -> recibirConClave(id, adminToken, clave, new long[] {linea, 2}));
        assertThat(estados).containsOnlyOnce(201);
        assertThat(estados).filteredOn(e -> e != 201).allMatch(e -> e == 200 || e == 409);
        assertThat(contar("SELECT COUNT(*) FROM recepciones")).isEqualTo(1);
        assertThat(existencias(tornillo)).isEqualTo(2);
    }

    // ---------------------------------------------------------------- ajustes

    @Test
    void losAjustesExigenMotivoNoDejanExistenciasNegativasYSonIdempotentes() throws Exception {
        String clave = UUID.randomUUID().toString();
        JsonNode inicial = leer(ajustarConClave(adminToken, tornillo, 20, "Inventario inicial", clave)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("AJUSTE"))
                .andExpect(jsonPath("$.saldoResultante").value(20)));
        ajustarConClave(adminToken, tornillo, 20, "Inventario inicial", clave)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(inicial.get("id").asLong()));
        ajustarConClave(adminToken, tornillo, 5, "Inventario inicial", clave).andExpect(status().isConflict());
        assertThat(existencias(tornillo)).isEqualTo(20);

        ajustar(adminToken, tornillo, -25, "Merma")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Existencias insuficientes: «Tornillo» tiene 20 unidades y no se pueden retirar 25."));
        ajustar(adminToken, tornillo, -8, "Merma por óxido").andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldoResultante").value(12));
        ajustar(adminToken, tornillo, 0, "Nada").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.cantidad").exists());
        ajustar(adminToken, tornillo, 3, "  ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.motivo").exists());
        postJson(INVENTARIO + "/ajustes", adminToken, json(Map.of("productoId", tornillo, "cantidad", -1, "motivo", "Salida",
                "costoUnitario", 100, "claveIdempotencia", UUID.randomUUID().toString())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.costoUnitario").exists());
        assertThat(existencias(tornillo)).isEqualTo(12);
        // El producto con unidades no se puede dar de baja.
        deleteJson("/api/v1/productos/" + tornillo, adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("quedan 12 unidades")));
    }

    @Test
    void retirosSimultaneosNuncaDejanExistenciasNegativas() throws Exception {
        ajustar(adminToken, tornillo, 10, "Conteo").andExpect(status().isCreated());
        // Cinco retiros de 3 a la vez sobre 10 unidades: caben exactamente tres.
        List<Integer> estados = enParalelo(5, i -> () -> ajustar(adminToken, tornillo, -3, "Salida " + i));
        assertThat(estados).filteredOn(e -> e == 201).hasSize(3);
        assertThat(existencias(tornillo)).isEqualTo(1);
        // Cada saldo del kárdex es distinto y coherente: 10 → 7 → 4 → 1.
        assertThat(jdbcTemplate.queryForList("SELECT saldo_resultante FROM movimientos_inventario WHERE producto_id = ? "
                + "ORDER BY id", Integer.class, tornillo)).containsExactly(10, 7, 4, 1);
    }

    @Test
    void elInventarioSeFiltraPorEstadoYTextoYSeCreaConCadaProducto() throws Exception {
        putJson("/api/v1/productos/" + tuerca, adminToken, """
                {"sku": "TUE-01", "nombre": "Tuerca", "precio": 250, "stockMinimo": 10, "proveedorId": %d}
                """.formatted(proveedor)).andExpect(status().isOk());
        ajustar(adminToken, tuerca, 4, "Conteo").andExpect(status().isCreated());
        ajustar(adminToken, tornillo, 50, "Conteo").andExpect(status().isCreated());
        long arandela = crearProducto("ARA-01", "Arandela", "50", proveedor);
        assertThat(existencias(arandela)).isZero();

        getJson(INVENTARIO, usuarioToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].nombre").value("Arandela"))
                .andExpect(jsonPath("$.content[0].estado").value("AGOTADO"));
        getJson(INVENTARIO + "?estado=CRITICO", adminToken)
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("TUE-01"))
                .andExpect(jsonPath("$.content[0].stockMinimo").value(10));
        getJson(INVENTARIO + "?estado=NORMAL", adminToken)
                .andExpect(jsonPath("$.content[0].sku").value("TOR-01"))
                .andExpect(jsonPath("$.content[0].cantidad").value(50));
        getJson(INVENTARIO + "?q=tor", adminToken).andExpect(jsonPath("$.page.totalElements").value(1));
        getJson(INVENTARIO + "?q=100%25", adminToken).andExpect(jsonPath("$.page.totalElements").value(0));
    }

    // ---------------------------------------------------------------- permisos

    @Test
    void elTrabajadorConsultaExistenciasPeroNoElKardexNiAjusta() throws Exception {
        getJson(INVENTARIO, usuarioToken).andExpect(status().isOk());
        getJson(INVENTARIO + "/movimientos", usuarioToken).andExpect(status().isForbidden());
        ajustar(usuarioToken, tornillo, 5, "Intento").andExpect(status().isForbidden());
        JsonNode orden = ordenEmitida(proveedor, tornillo, 5);
        recibir(orden.get("id").asLong(), usuarioToken, new long[] {orden.at("/detalles/0/id").asLong(), 5})
                .andExpect(status().isForbidden());
        getJson("/api/v1/recepciones", usuarioToken).andExpect(status().isForbidden());

        String superAdmin = crearUsuarioYEntrar("plataforma", Rol.SUPER_ADMIN, null);
        getEnEmpresa(INVENTARIO + "/movimientos", superAdmin, empresa.getId()).andExpect(status().isOk());
        postEnEmpresa(INVENTARIO + "/ajustes", superAdmin, empresa.getId(), json(Map.of("productoId", tornillo,
                "cantidad", 5, "motivo", "Soporte", "claveIdempotencia", UUID.randomUUID().toString())))
                .andExpect(status().isForbidden());
        assertThat(existencias(tornillo)).isZero();
    }

    // ---------------------------------------------------------------- aislamiento

    @Test
    void otraEmpresaNoVeNiMueveElInventarioAjeno() throws Exception {
        ajustar(adminToken, tornillo, 7, "Conteo").andExpect(status().isCreated());
        Empresa norte = crearEmpresa("Ferretería Norte", "800000002-2");
        String intruso = crearUsuarioYEntrar("admin.norte", Rol.ADMIN_EMPRESA, norte);

        getJson(INVENTARIO, intruso).andExpect(jsonPath("$.page.totalElements").value(0));
        getJson(INVENTARIO + "/movimientos", intruso).andExpect(jsonPath("$.page.totalElements").value(0));
        getJson(INVENTARIO + "/movimientos?productoId=" + tornillo, intruso).andExpect(jsonPath("$.page.totalElements").value(0));
        getJson("/api/v1/recepciones", intruso).andExpect(jsonPath("$.page.totalElements").value(0));
        ajustar(intruso, tornillo, -7, "Sabotaje")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.productoId").exists());
        assertThat(existencias(tornillo)).isEqualTo(7);
    }
}
