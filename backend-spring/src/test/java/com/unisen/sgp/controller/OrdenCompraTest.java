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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Órdenes de compra (Entrega B): numeración, validación de proveedor y productos, transiciones
 * de estado en el backend, generación desde solicitudes, permisos y aislamiento entre empresas
 * con ids manipulados.
 */
class OrdenCompraTest extends ComprasApiTest {

    private long aceros;
    private long pinturas;
    private long tornillo;
    private long tuerca;
    private long pintura;

    @BeforeEach
    void prepararCatalogo() throws Exception {
        aceros = crearProveedor("900111111-1", "Aceros Andinos");
        pinturas = crearProveedor("900222222-2", "Pinturas del Valle");
        tornillo = crearProducto("TOR-01", "Tornillo", "1250.50", aceros);
        tuerca = crearProducto("TUE-01", "Tuerca", "300", aceros);
        pintura = crearProducto("PIN-01", "Pintura blanca", "45000", pinturas);
    }

    // ---------------------------------------------------------------- alta y validación

    @Test
    void creaUnBorradorNumeradoConElCostoDelCatalogoPorDefecto() throws Exception {
        String body = json(Map.of("proveedorId", aceros, "observaciones", "  Entrega en bodega  ", "detalles", List.of(
                Map.of("productoId", tornillo, "cantidad", 10),
                Map.of("productoId", tuerca, "cantidad", 4, "costoUnitario", 280))));
        JsonNode orden = leer(postJson(ORDENES, adminToken, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.proveedor.razonSocial").value("Aceros Andinos"))
                .andExpect(jsonPath("$.observaciones").value("Entrega en bodega"))
                .andExpect(jsonPath("$.creadoPor.username").value("admin"))
                .andExpect(jsonPath("$.emitidaEn").doesNotExist()));
        assertThat(orden.at("/detalles/0/costoUnitario").decimalValue()).isEqualByComparingTo("1250.50");
        assertThat(orden.at("/detalles/1/costoUnitario").decimalValue()).isEqualByComparingTo("280");
        // 10 × 1250,50 + 4 × 280
        assertThat(orden.get("total").decimalValue()).isEqualByComparingTo("13625.00");
        assertThat(orden.get("unidadesPedidas").asInt()).isEqualTo(14);
        assertThat(crearOrden(adminToken, aceros, new long[] {tornillo, 1}).get("numero").asLong()).isEqualTo(2);
    }

    @Test
    void cadaOrdenEsDeUnSoloProveedorConProductosActivosYSinRepetir() throws Exception {
        postJson(ORDENES, adminToken, json(Map.of("proveedorId", aceros, "detalles", List.of(
                Map.of("productoId", tornillo, "cantidad", 1), Map.of("productoId", pintura, "cantidad", 1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['detalles[1].productoId']")
                        .value("«Pintura blanca» no es de Aceros Andinos: cada orden es de un solo proveedor."));
        postJson(ORDENES, adminToken, json(Map.of("proveedorId", aceros, "detalles", List.of(
                Map.of("productoId", tornillo, "cantidad", 1), Map.of("productoId", tornillo, "cantidad", 2)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['detalles[1].productoId']").exists());
        postJson(ORDENES, adminToken, json(Map.of("proveedorId", 999_999, "detalles", List.of(
                Map.of("productoId", tornillo, "cantidad", 1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.proveedorId").exists());
        postJson(ORDENES, adminToken, json(Map.of("proveedorId", aceros, "detalles", List.of(
                Map.of("productoId", tornillo, "cantidad", 0)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['detalles[0].cantidad']").exists());
        postJson(ORDENES, adminToken, json(Map.of("proveedorId", aceros, "detalles", List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.detalles").exists());
        deleteJson("/api/v1/productos/" + tuerca, adminToken).andExpect(status().isNoContent());
        postJson(ORDENES, adminToken, json(Map.of("proveedorId", aceros, "detalles", List.of(
                Map.of("productoId", tuerca, "cantidad", 1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['detalles[0].productoId']").value("El producto no existe o fue dado de baja."));
        assertThat(contar("SELECT COUNT(*) FROM ordenes_compra")).isZero();
    }

    @Test
    void elBorradorSeEditaEnSuSitioYDejaDeEditarseAlEmitir() throws Exception {
        JsonNode orden = crearOrden(adminToken, aceros, new long[] {tornillo, 10}, new long[] {tuerca, 5});
        long id = orden.get("id").asLong();
        long lineaTornillo = orden.at("/detalles/0/id").asLong();
        // Mismo producto con otra cantidad (se actualiza la línea, sin chocar con el UNIQUE), y la tuerca sale.
        JsonNode editada = leer(putJson(ORDENES + "/" + id, adminToken, json(Map.of("observaciones", "Urgente",
                "detalles", List.of(Map.of("productoId", tornillo, "cantidad", 20, "costoUnitario", 1100))))).andExpect(status().isOk()));
        assertThat(editada.get("detalles")).hasSize(1);
        assertThat(editada.at("/detalles/0/id").asLong()).isEqualTo(lineaTornillo);
        assertThat(editada.at("/detalles/0/cantidad").asInt()).isEqualTo(20);
        assertThat(editada.get("total").decimalValue()).isEqualByComparingTo("22000");
        assertThat(editada.get("observaciones").asText()).isEqualTo("Urgente");

        emitir(id, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EMITIDA"))
                .andExpect(jsonPath("$.emitidaPor.username").value("admin"))
                .andExpect(jsonPath("$.emitidaEn").exists());
        putJson(ORDENES + "/" + id, adminToken, json(Map.of("detalles", List.of(Map.of("productoId", tornillo, "cantidad", 1)))))
                .andExpect(status().isConflict());
        emitir(id, adminToken).andExpect(status().isConflict());
    }

    @Test
    void cancelarExigeMotivoYSoloDesdeBorradorOEmitida() throws Exception {
        long borrador = crearOrden(adminToken, aceros, new long[] {tornillo, 1}).get("id").asLong();
        cancelar(borrador, adminToken, "   ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.motivo").exists());
        cancelar(borrador, adminToken, "Ya no se necesita")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.motivoCancelacion").value("Ya no se necesita"))
                .andExpect(jsonPath("$.canceladaPor.username").value("admin"));
        cancelar(borrador, adminToken, "Otra vez").andExpect(status().isConflict());
        emitir(borrador, adminToken).andExpect(status().isConflict());

        JsonNode emitida = ordenEmitida(aceros, tornillo, 5);
        cancelar(emitida.get("id").asLong(), adminToken, "Proveedor sin stock")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emitidaEn").exists());

        JsonNode parcial = ordenEmitida(aceros, tornillo, 5);
        recibir(parcial.get("id").asLong(), adminToken, new long[] {parcial.at("/detalles/0/id").asLong(), 2})
                .andExpect(status().isCreated());
        cancelar(parcial.get("id").asLong(), adminToken, "Tarde")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("mercancía recibida")));
    }

    @Test
    void alEmitirSeVuelveAComprobarElCatalogo() throws Exception {
        long id = crearOrden(adminToken, aceros, new long[] {tornillo, 1}).get("id").asLong();
        // El producto cambia de proveedor después de crear el borrador.
        putJson("/api/v1/productos/" + tornillo, adminToken, """
                {"sku": "TOR-01", "nombre": "Tornillo", "precio": 1250.50, "proveedorId": %d}
                """.formatted(pinturas)).andExpect(status().isOk());
        emitir(id, adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("«Tornillo» ya no es de Aceros Andinos: quítalo de la orden."));
    }

    @Test
    void lasOrdenesAbiertasImpidenDarDeBajaSuProductoYSuProveedor() throws Exception {
        long id = crearOrden(adminToken, aceros, new long[] {tuerca, 1}).get("id").asLong();
        deleteJson("/api/v1/productos/" + tuerca, adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No se puede eliminar el producto: está incluido en 1 orden de compra abierta."));
        // Sin productos activos del proveedor, la orden abierta sigue impidiendo darlo de baja.
        putJson("/api/v1/productos/" + tornillo, adminToken, """
                {"sku": "TOR-01", "nombre": "Tornillo", "precio": 10, "proveedorId": %d}
                """.formatted(pinturas)).andExpect(status().isOk());
        putJson("/api/v1/productos/" + tuerca, adminToken, """
                {"sku": "TUE-01", "nombre": "Tuerca", "precio": 10, "proveedorId": %d}
                """.formatted(pinturas)).andExpect(status().isOk());
        deleteJson("/api/v1/proveedores/" + aceros, adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("1 orden de compra abierta")));
        cancelar(id, adminToken, "Cambio de proveedor").andExpect(status().isOk());
        deleteJson("/api/v1/proveedores/" + aceros, adminToken).andExpect(status().isNoContent());
        // La orden histórica se sigue leyendo con su proveedor dado de baja.
        getJson(ORDENES + "/" + id, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proveedor.razonSocial").value("Aceros Andinos"))
                .andExpect(jsonPath("$.proveedor.activo").value(false));
    }

    // ---------------------------------------------------------------- desde una solicitud

    @Test
    void generaUnBorradorPorProveedorDesdeUnaSolicitudAprobadaYSoloUnaVez() throws Exception {
        long solicitud = leer(postJson("/api/v1/solicitudes", usuarioToken, json(Map.of("justificacion", "Obra del bloque B",
                "detalles", List.of(Map.of("productoId", tornillo, "cantidad", 50), Map.of("productoId", pintura, "cantidad", 3),
                        Map.of("productoId", tuerca, "cantidad", 50)))))).get("id").asLong();
        String url = ORDENES + "/desde-solicitud/" + solicitud;
        postJson(url, adminToken, "").andExpect(status().isConflict()); // aún pendiente
        patchJson("/api/v1/solicitudes/" + solicitud + "/estado", adminToken, "{\"estado\": \"APROBADA\"}")
                .andExpect(status().isOk());
        postJson(url, usuarioToken, "").andExpect(status().isForbidden());

        JsonNode ordenes = leer(postJson(url, adminToken, "").andExpect(status().isCreated()));
        assertThat(ordenes).hasSize(2);
        assertThat(ordenes.findValuesAsText("razonSocial")).containsExactlyInAnyOrder("Aceros Andinos", "Pinturas del Valle");
        ordenes.forEach(orden -> {
            assertThat(orden.get("estado").asText()).isEqualTo("BORRADOR");
            assertThat(orden.get("solicitudId").asLong()).isEqualTo(solicitud);
        });
        JsonNode deAceros = ordenes.get(0).at("/proveedor/razonSocial").asText().equals("Aceros Andinos") ? ordenes.get(0) : ordenes.get(1);
        assertThat(deAceros.get("detalles")).hasSize(2);
        assertThat(deAceros.get("unidadesPedidas").asInt()).isEqualTo(100);
        assertThat(ordenes.findValues("numero").stream().map(JsonNode::asLong)).containsExactlyInAnyOrder(1L, 2L);

        // No se duplican; cancelándolas, se pueden volver a generar.
        postJson(url, adminToken, "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("OC-00001")));
        getJson(ORDENES + "?solicitudId=" + solicitud, adminToken).andExpect(jsonPath("$.page.totalElements").value(2));
        for (JsonNode orden : ordenes) {
            cancelar(orden.get("id").asLong(), adminToken, "Rehacer").andExpect(status().isOk());
        }
        assertThat(leer(postJson(url, adminToken, "").andExpect(status().isCreated()))).hasSize(2);
    }

    @Test
    void dosGeneracionesSimultaneasDeLaMismaSolicitudNoDuplicanOrdenes() throws Exception {
        long solicitud = leer(postJson("/api/v1/solicitudes", usuarioToken, json(Map.of("justificacion", "Reposición mensual",
                "detalles", List.of(Map.of("productoId", tornillo, "cantidad", 5)))))).get("id").asLong();
        patchJson("/api/v1/solicitudes/" + solicitud + "/estado", adminToken, "{\"estado\": \"APROBADA\"}");
        List<Integer> estados = enParalelo(4, i -> () -> postJson(ORDENES + "/desde-solicitud/" + solicitud, adminToken, ""));
        assertThat(estados).containsOnlyOnce(201);
        assertThat(estados).filteredOn(e -> e != 201).containsOnly(409);
        assertThat(contar("SELECT COUNT(*) FROM ordenes_compra WHERE solicitud_id = ?", solicitud)).isEqualTo(1);
    }

    @Test
    void altasSimultaneasRecibenNumerosCorrelativosSinHuecosNiRepetidos() throws Exception {
        String body = json(Map.of("proveedorId", aceros, "detalles", List.of(Map.of("productoId", tornillo, "cantidad", 1))));
        List<Integer> estados = enParalelo(6, i -> () -> postJson(ORDENES, adminToken, body));
        assertThat(estados).containsOnly(201);
        assertThat(jdbcTemplate.queryForList("SELECT numero FROM ordenes_compra ORDER BY numero", Long.class))
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
    }

    // ---------------------------------------------------------------- permisos

    @Test
    void elTrabajadorNoAccedeYElSuperAdminSoloConsulta() throws Exception {
        long id = crearOrden(adminToken, aceros, new long[] {tornillo, 1}).get("id").asLong();
        getJson(ORDENES, usuarioToken).andExpect(status().isForbidden());
        getJson(ORDENES + "/" + id, usuarioToken).andExpect(status().isForbidden());
        postJson(ORDENES, usuarioToken, json(Map.of("proveedorId", aceros,
                "detalles", List.of(Map.of("productoId", tornillo, "cantidad", 1))))).andExpect(status().isForbidden());

        String superAdmin = crearUsuarioYEntrar("plataforma", Rol.SUPER_ADMIN, null);
        getEnEmpresa(ORDENES + "/" + id, superAdmin, empresa.getId()).andExpect(status().isOk());
        getGlobal(ORDENES, superAdmin).andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1));
        postEnEmpresa(ORDENES + "/" + id + "/emitir", superAdmin, empresa.getId(), "").andExpect(status().isForbidden());
        postEnEmpresa(ORDENES, superAdmin, empresa.getId(), json(Map.of("proveedorId", aceros,
                "detalles", List.of(Map.of("productoId", tornillo, "cantidad", 1))))).andExpect(status().isForbidden());
        assertThat(contar("SELECT COUNT(*) FROM ordenes_compra WHERE estado = 'BORRADOR'")).isEqualTo(1);
    }

    // ---------------------------------------------------------------- aislamiento entre empresas

    @Test
    void otraEmpresaNoVeNiModificaLasOrdenesNiUsaElCatalogoAjeno() throws Exception {
        long borrador = crearOrden(adminToken, aceros, new long[] {tornillo, 3}).get("id").asLong();
        JsonNode emitida = ordenEmitida(aceros, tuerca, 4);
        long emitidaId = emitida.get("id").asLong();
        long lineaEmitida = emitida.at("/detalles/0/id").asLong();
        long solicitud = leer(postJson("/api/v1/solicitudes", usuarioToken, json(Map.of("justificacion", "Material obra",
                "detalles", List.of(Map.of("productoId", tornillo, "cantidad", 1)))))).get("id").asLong();
        patchJson("/api/v1/solicitudes/" + solicitud + "/estado", adminToken, "{\"estado\": \"APROBADA\"}");

        Empresa norte = crearEmpresa("Ferretería Norte", "800000002-2");
        String intruso = crearUsuarioYEntrar("admin.norte", Rol.ADMIN_EMPRESA, norte);
        long proveedorNorte = leer(postJson("/api/v1/proveedores", intruso,
                "{\"nit\": \"900333333-3\", \"razonSocial\": \"Norteños\", \"email\": \"n@n.com\"}")).get("id").asLong();

        // Ids de Unisen manipulados en cada endpoint: 404, nunca 200 ni 403 (no se revela que existen).
        getJson(ORDENES + "/" + borrador, intruso).andExpect(status().isNotFound());
        getJson(ORDENES, intruso).andExpect(jsonPath("$.page.totalElements").value(0));
        putJson(ORDENES + "/" + borrador, intruso, json(Map.of("detalles", List.of(Map.of("productoId", tornillo, "cantidad", 9)))))
                .andExpect(status().isNotFound());
        emitir(borrador, intruso).andExpect(status().isNotFound());
        cancelar(emitidaId, intruso, "Sabotaje").andExpect(status().isNotFound());
        recibir(emitidaId, intruso, new long[] {lineaEmitida, 4}).andExpect(status().isNotFound());
        getJson(ORDENES + "/" + emitidaId + "/recepciones", intruso).andExpect(status().isNotFound());
        postJson(ORDENES + "/desde-solicitud/" + solicitud, intruso, "").andExpect(status().isNotFound());
        // Proveedor o productos de Unisen en una orden de Norte: campos inválidos.
        postJson(ORDENES, intruso, json(Map.of("proveedorId", aceros, "detalles", List.of(Map.of("productoId", tornillo, "cantidad", 1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.proveedorId").exists());
        postJson(ORDENES, intruso, json(Map.of("proveedorId", proveedorNorte, "detalles", List.of(Map.of("productoId", tornillo, "cantidad", 1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['detalles[0].productoId']").exists());

        // Nada cambió en Unisen.
        getJson(ORDENES + "/" + borrador, adminToken).andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.detalles[0].cantidad").value(3));
        getJson(ORDENES + "/" + emitidaId, adminToken).andExpect(jsonPath("$.estado").value("EMITIDA"));
        assertThat(existencias(tuerca)).isZero();
        assertThat(contar("SELECT COUNT(*) FROM ordenes_compra WHERE empresa_id = ?", norte.getId())).isZero();
        // La numeración es por empresa: la primera orden de Norte es la 1.
        long productoNorte = leer(postJson("/api/v1/productos", intruso, """
                {"sku": "NOR-01", "nombre": "Cable", "precio": 10, "proveedorId": %d}
                """.formatted(proveedorNorte))).get("id").asLong();
        assertThat(crearOrden(intruso, proveedorNorte, new long[] {productoNorte, 1}).get("numero").asLong()).isEqualTo(1);
    }
}
