package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.support.ComprasApiTest;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Programación de entregas y su supervisión: versiones con historial, motivo al reprogramar,
 * fecha estimada distinta de la confirmación del proveedor, retrasos, permisos, aislamiento y
 * cambios simultáneos.
 */
class EntregaTest extends ComprasApiTest {

    private static final String ENTREGAS = "/api/v1/entregas";

    private long proveedor;
    private long tornillo;

    @BeforeEach
    void prepararCatalogo() throws Exception {
        proveedor = crearProveedor("900111111-1", "Aceros Andinos");
        tornillo = crearProducto("TOR-01", "Tornillo", "1000", proveedor);
    }

    @Test
    void programarYReprogramarConservaElHistorialYLaFechaVigente() throws Exception {
        long id = ordenEmitida(proveedor, tornillo, 10).get("id").asLong();

        programar(id, adminToken, 3, Map.of("horaDesde", "08:00", "horaHasta", "12:00",
                "referenciaProveedor", "REM-551", "observaciones", "Muelle 2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.fechaEstimada").value(hoy().plusDays(3).toString()))
                .andExpect(jsonPath("$.horaDesde").value("08:00"))
                .andExpect(jsonPath("$.horaHasta").value("12:00"))
                .andExpect(jsonPath("$.confirmadaProveedor").value(false))
                .andExpect(jsonPath("$.registradaPor.username").value("admin"));

        // Reprogramar exige motivo…
        programar(id, adminToken, 5, Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.motivo").exists());
        // …y con él crea la versión 2, que pasa a ser la vigente (sin franja: todo el día).
        programar(id, adminToken, 5, Map.of("motivo", "El proveedor pidió dos días más", "confirmadaProveedor", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.horaDesde").doesNotExist())
                .andExpect(jsonPath("$.confirmadaProveedor").value(true));
        // Repetir exactamente la vigente no crea otra versión.
        programar(id, adminToken, 5, Map.of("motivo", "Otra vez", "confirmadaProveedor", true))
                .andExpect(jsonPath("$.version").value(2));

        getJson(ORDENES + "/" + id + "/entrega/historial", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fechaEstimada").value(hoy().plusDays(3).toString()))
                .andExpect(jsonPath("$[0].referenciaProveedor").value("REM-551"))
                .andExpect(jsonPath("$[1].motivoCambio").value("El proveedor pidió dos días más"));
        // La orden muestra la vigente; la fecha estimada no es una recepción.
        getJson(ORDENES + "/" + id, adminToken)
                .andExpect(jsonPath("$.entrega.version").value(2))
                .andExpect(jsonPath("$.entrega.fechaEstimada").value(hoy().plusDays(5).toString()))
                .andExpect(jsonPath("$.estado").value("EMITIDA"));
        assertThat(contar("SELECT COUNT(*) FROM recepciones")).isZero();
    }

    @Test
    void validaFechasYFranjas() throws Exception {
        long id = ordenEmitida(proveedor, tornillo, 10).get("id").asLong();
        programar(id, adminToken, -1, Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fechaEstimada").exists());
        programar(id, adminToken, 365 * 3, Map.of()).andExpect(status().isBadRequest());
        programar(id, adminToken, 1, Map.of("horaDesde", "10:00")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.horaHasta").exists());
        programar(id, adminToken, 1, Map.of("horaDesde", "14:00", "horaHasta", "09:00")).andExpect(status().isBadRequest());
        putJson(ORDENES + "/" + id + "/entrega", adminToken, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fechaEstimada").exists());

        // Confirmar con el proveedor una entrega ya vencida sin moverla sí se permite.
        programar(id, adminToken, 0, Map.of()).andExpect(status().isOk());
        moverFechaEstimada(id, -2);
        putJson(ORDENES + "/" + id + "/entrega", adminToken, json(Map.of("fechaEstimada", hoy().minusDays(2).toString(),
                "confirmadaProveedor", true, "motivo", "Confirmó que llega hoy tarde"))).andExpect(status().isOk());
        assertThat(contar("SELECT COUNT(*) FROM programaciones_entrega WHERE orden_id = ?", id)).isEqualTo(2);
    }

    @Test
    void soloSeProgramanOrdenesVivasYSoloLoHaceElAdministrador() throws Exception {
        long borrador = crearOrden(adminToken, proveedor, new long[] {tornillo, 1}).get("id").asLong();
        programar(borrador, adminToken, 2, Map.of()).andExpect(status().isOk());

        JsonNode emitida = ordenEmitida(proveedor, tornillo, 2);
        long id = emitida.get("id").asLong();
        programar(id, usuarioToken, 2, Map.of()).andExpect(status().isForbidden());
        String plataforma = crearUsuarioYEntrar("plataforma", Rol.SUPER_ADMIN, null);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put(ORDENES + "/" + id + "/entrega")
                        .header("Authorization", "Bearer " + plataforma)
                        .header("X-Tenant-ID", empresa.getId().toString())
                        .contentType("application/json")
                        .content(json(Map.of("fechaEstimada", hoy().plusDays(2).toString()))))
                .andExpect(status().isForbidden());

        recibir(id, adminToken, new long[] {emitida.at("/detalles/0/id").asLong(), 2}).andExpect(status().isCreated());
        programar(id, adminToken, 2, Map.of()).andExpect(status().isConflict());
        long cancelada = crearOrden(adminToken, proveedor, new long[] {tornillo, 1}).get("id").asLong();
        cancelar(cancelada, adminToken, "No se necesita").andExpect(status().isOk());
        programar(cancelada, adminToken, 2, Map.of()).andExpect(status().isConflict());
        assertThat(contar("SELECT COUNT(*) FROM programaciones_entrega")).isEqualTo(1);
    }

    @Test
    void elListadoDistingueProgramadasRetrasadasParcialesYCompletas() throws Exception {
        long sinFecha = ordenEmitida(proveedor, tornillo, 5).get("id").asLong();
        long aTiempo = ordenEmitida(proveedor, tornillo, 5).get("id").asLong();
        programar(aTiempo, adminToken, 4, Map.of()).andExpect(status().isOk());
        long retrasada = ordenEmitida(proveedor, tornillo, 5).get("id").asLong();
        programar(retrasada, adminToken, 0, Map.of()).andExpect(status().isOk());
        moverFechaEstimada(retrasada, -3);
        JsonNode parcial = ordenEmitida(proveedor, tornillo, 5);
        long parcialId = parcial.get("id").asLong();
        programar(parcialId, adminToken, 1, Map.of()).andExpect(status().isOk());
        recibir(parcialId, usuarioToken, new long[] {parcial.at("/detalles/0/id").asLong(), 2}).andExpect(status().isCreated());
        JsonNode completa = ordenEmitida(proveedor, tornillo, 5);
        recibir(completa.get("id").asLong(), usuarioToken, new long[] {completa.at("/detalles/0/id").asLong(), 5})
                .andExpect(status().isCreated());
        crearOrden(adminToken, proveedor, new long[] {tornillo, 1}); // borrador: no es una entrega

        // Pendientes: por fecha estimada (la retrasada primero) y las sin programar al final.
        JsonNode pendientes = leer(getJson(ENTREGAS, usuarioToken).andExpect(status().isOk()));
        assertThat(ids(pendientes)).containsExactly(retrasada, parcialId, aTiempo, sinFecha);
        JsonNode primera = pendientes.at("/content/0");
        assertThat(primera.get("situacion").asText()).isEqualTo("RETRASADA");
        assertThat(primera.get("diasRetraso").asInt()).isEqualTo(3);
        assertThat(primera.get("alertas").toString()).contains("RETRASADA");
        // Sin costos en la vista operativa.
        assertThat(pendientes.get("content").toString()).doesNotContain("costo").doesNotContain("\"total\"")
                .doesNotContain("valor");

        assertThat(ids(leer(getJson(ENTREGAS + "?vista=RETRASADAS", usuarioToken)))).containsExactly(retrasada);
        assertThat(ids(leer(getJson(ENTREGAS + "?vista=PROGRAMADAS", usuarioToken)))).containsExactly(parcialId, aTiempo);
        assertThat(ids(leer(getJson(ENTREGAS + "?vista=SIN_PROGRAMAR", usuarioToken)))).containsExactly(sinFecha);
        JsonNode parciales = leer(getJson(ENTREGAS + "?vista=PARCIALES", usuarioToken));
        assertThat(ids(parciales)).containsExactly(parcialId);
        assertThat(parciales.at("/content/0/unidadesPendientes").asInt()).isEqualTo(3);
        assertThat(parciales.at("/content/0/recepciones").asInt()).isEqualTo(1);
        assertThat(ids(leer(getJson(ENTREGAS + "?vista=COMPLETAS", adminToken)))).containsExactly(completa.get("id").asLong());
        assertThat(ids(leer(getJson(ENTREGAS + "?vista=INTERVENCION", adminToken)))).containsExactly(retrasada, sinFecha);
        // Filtros por rango de fecha estimada, proveedor y número.
        assertThat(ids(leer(getJson(ENTREGAS + "?desde=" + hoy() + "&hasta=" + hoy().plusDays(1), adminToken))))
                .containsExactly(parcialId);
        assertThat(ids(leer(getJson(ENTREGAS + "?numero=" + leer(getJson(ORDENES + "/" + aTiempo, adminToken))
                .get("numero").asLong(), adminToken)))).containsExactly(aTiempo);
        assertThat(leer(getJson(ENTREGAS + "?proveedorId=999999", adminToken)).at("/page/totalElements").asInt()).isZero();
        getJson(ENTREGAS + "?desde=" + hoy() + "&hasta=" + hoy().minusDays(1), adminToken).andExpect(status().isBadRequest());

        // Resumen de supervisión.
        getJson(ENTREGAS + "/resumen", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.pendientes").value(4))
                .andExpect(jsonPath("$.retrasadas").value(1))
                .andExpect(jsonPath("$.sinProgramar").value(1))
                .andExpect(jsonPath("$.parciales").value(1))
                .andExpect(jsonPath("$.requierenIntervencion").value(2));
        // El trabajador consulta lo pendiente, no la supervisión.
        getJson(ENTREGAS + "?vista=COMPLETAS", usuarioToken).andExpect(status().isForbidden());
        getJson(ENTREGAS + "/resumen", usuarioToken).andExpect(status().isForbidden());
    }

    @Test
    void lasEntregasDeOtraEmpresaNoSeVenNiSeModifican() throws Exception {
        long id = ordenEmitida(proveedor, tornillo, 5).get("id").asLong();
        programar(id, adminToken, 2, Map.of()).andExpect(status().isOk());

        Empresa norte = crearEmpresa("Norte", "900999999-9");
        String intruso = crearUsuarioYEntrar("admin.norte", Rol.ADMINISTRADOR_COMERCIAL, norte);
        String trabajadorNorte = crearUsuarioYEntrar("ana.norte", Rol.TRABAJADOR, norte);
        getJson(ENTREGAS, intruso).andExpect(jsonPath("$.page.totalElements").value(0));
        getJson(ENTREGAS, trabajadorNorte).andExpect(jsonPath("$.page.totalElements").value(0));
        getJson(ENTREGAS + "/" + id, trabajadorNorte).andExpect(status().isNotFound());
        getJson(ORDENES + "/" + id + "/entrega/historial", intruso).andExpect(status().isNotFound());
        programar(id, intruso, 9, Map.of("motivo", "Manipulación")).andExpect(status().isNotFound());
        assertThat(contar("SELECT COUNT(*) FROM programaciones_entrega WHERE orden_id = ?", id)).isEqualTo(1);

        // SUPER_ADMIN: supervisando, consulta; en modo global, el listado exige elegir empresa.
        String plataforma = crearUsuarioYEntrar("plataforma", Rol.SUPER_ADMIN, null);
        getEnEmpresa(ENTREGAS, plataforma, empresa.getId()).andExpect(jsonPath("$.page.totalElements").value(1));
        getGlobal(ENTREGAS, plataforma).andExpect(status().isBadRequest());
    }

    @Test
    void elTrabajadorVeElDetalleSinCostosYNoVeBorradores() throws Exception {
        JsonNode orden = ordenEmitida(proveedor, tornillo, 6);
        long id = orden.get("id").asLong();
        programar(id, adminToken, 1, Map.of("observaciones", "Llamar al llegar")).andExpect(status().isOk());

        JsonNode detalle = leer(getJson(ENTREGAS + "/" + id, usuarioToken).andExpect(status().isOk()));
        assertThat(detalle.at("/lineas/0/pendiente").asInt()).isEqualTo(6);
        assertThat(detalle.at("/entrega/programacion/observaciones").asText()).isEqualTo("Llamar al llegar");
        assertThat(detalle.at("/programaciones").size()).isEqualTo(1);
        assertThat(detalle.toString()).doesNotContain("costo").doesNotContain("subtotal").doesNotContain("valor");

        long borrador = crearOrden(adminToken, proveedor, new long[] {tornillo, 1}).get("id").asLong();
        getJson(ENTREGAS + "/" + borrador, usuarioToken).andExpect(status().isNotFound());
        getJson(ENTREGAS + "/" + borrador, adminToken).andExpect(status().isOk());
        // La ficha completa de la orden (con costos) sigue siendo de gestión.
        getJson(ORDENES + "/" + id, usuarioToken).andExpect(status().isForbidden());
    }

    @Test
    void reprogramacionesSimultaneasNoCompartenVersion() throws Exception {
        long id = ordenEmitida(proveedor, tornillo, 5).get("id").asLong();
        programar(id, adminToken, 1, Map.of()).andExpect(status().isOk());
        List<Integer> estados = enParalelo(4, i -> () -> programar(id, adminToken, 10 + i,
                Map.of("motivo", "Cambio " + i)));
        assertThat(estados).containsOnly(200);
        List<Integer> versiones = jdbcTemplate.queryForList(
                "SELECT version FROM programaciones_entrega WHERE orden_id = ? ORDER BY version", Integer.class, id);
        assertThat(versiones).containsExactly(1, 2, 3, 4, 5);
        // La vigente es la última versión.
        assertThat(contar("SELECT p.version FROM ordenes_compra o JOIN programaciones_entrega p "
                + "ON p.id = o.programacion_entrega_id WHERE o.id = ?", id)).isEqualTo(5);
    }

    private static List<Long> ids(JsonNode pagina) {
        List<Long> ids = new java.util.ArrayList<>();
        Set<Long> vistos = new HashSet<>();
        pagina.get("content").forEach(e -> {
            long id = e.get("ordenId").asLong();
            if (vistos.add(id)) {
                ids.add(id);
            }
        });
        return ids;
    }
}
