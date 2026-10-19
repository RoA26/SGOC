package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.support.ComprasApiTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Recepción operativa del trabajador: identidad y fecha del servidor, respuesta sin costos,
 * faltantes, daños, excedentes y productos incorrectos, cantidades pendientes, idempotencia,
 * concurrencia, trazabilidad hasta el kárdex e inmutabilidad.
 */
class RecepcionOperativaTest extends ComprasApiTest {

    private long proveedor;
    private long tornillo;
    private long tuerca;

    @BeforeEach
    void prepararCatalogo() throws Exception {
        proveedor = crearProveedor("900111111-1", "Aceros Andinos");
        tornillo = crearProducto("TOR-01", "Tornillo", "1000", proveedor);
        tuerca = crearProducto("TUE-01", "Tuerca", "250", proveedor);
    }

    @Test
    void elTrabajadorRegistraConIdentidadYFechaDelServidorYSinVerCostos() throws Exception {
        JsonNode orden = ordenEmitida(proveedor, tornillo, 10);
        long id = orden.get("id").asLong();
        long linea = orden.at("/detalles/0/id").asLong();
        long adminId = contar("SELECT id FROM usuarios WHERE username = 'admin'");
        Empresa norte = crearEmpresa("Norte", "900999999-9");

        // Campos de identidad, empresa y fecha enviados por el cliente: se ignoran.
        String body = json(Map.of("claveIdempotencia", UUID.randomUUID().toString(),
                "recibidaPorId", adminId, "recibidaPor", Map.of("id", adminId), "empresaId", norte.getId(),
                "recibidaEn", "2000-01-01T00:00:00Z",
                "detalles", List.of(Map.of("detalleOrdenId", linea, "cantidad", 4))));
        Instant antes = Instant.now();
        JsonNode recepcion = leer(postJson(ORDENES + "/" + id + "/recepciones", usuarioToken, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recibidaPor.username").value("compras"))
                .andExpect(jsonPath("$.unidades").value(4))
                .andExpect(jsonPath("$.valor").doesNotExist())
                .andExpect(jsonPath("$.detalles[0].costoUnitario").doesNotExist()));
        assertThat(Duration.between(antes, Instant.parse(recepcion.get("recibidaEn").asText())).abs())
                .isLessThan(Duration.ofMinutes(1));
        assertThat(contar("SELECT empresa_id FROM recepciones")).isEqualTo(empresa.getId().intValue());
        assertThat(existencias(tornillo)).isEqualTo(4);

        // Su historial personal y el detalle, también sin costos; la supervisión (con costos) no.
        getJson("/api/v1/recepciones/mias", usuarioToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].valor").doesNotExist());
        getJson("/api/v1/recepciones/" + recepcion.get("id").asLong(), usuarioToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").doesNotExist());
        getJson("/api/v1/recepciones/" + recepcion.get("id").asLong(), adminToken)
                .andExpect(jsonPath("$.valor").value(4000));
        // El gerente ve quién la registró.
        getJson("/api/v1/recepciones?registradaPorId=" + contar("SELECT id FROM usuarios WHERE username = 'compras'"),
                adminToken).andExpect(jsonPath("$.content[0].recibidaPor.username").value("compras"));
        // "Mis recepciones" del administrador no incluye las del trabajador.
        getJson("/api/v1/recepciones/mias", adminToken).andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void faltantesDanosYExcedentesQuedanRegistradosSinMoverInventario() throws Exception {
        JsonNode orden = leer(emitir(crearOrden(adminToken, proveedor, new long[] {tornillo, 10}, new long[] {tuerca, 6})
                .get("id").asLong(), adminToken));
        long id = orden.get("id").asLong();
        long lTornillo = orden.at("/detalles/0/id").asLong();
        long lTuerca = orden.at("/detalles/1/id").asLong();

        JsonNode recepcion = leer(recibirConIncidencias(id, usuarioToken, UUID.randomUUID().toString(),
                List.of(new long[] {lTornillo, 6}, new long[] {lTuerca, 6}),
                List.of(Map.of("tipo", "DANADO", "detalleOrdenId", lTornillo, "cantidad", 2),
                        Map.of("tipo", "FALTANTE", "detalleOrdenId", lTornillo, "cantidad", 2),
                        Map.of("tipo", "EXCEDENTE", "detalleOrdenId", lTuerca, "cantidad", 3),
                        Map.of("tipo", "PRODUCTO_INCORRECTO", "cantidad", 1, "descripcion", "Llegó una arandela"),
                        Map.of("tipo", "OTRO", "descripcion", "Caja golpeada")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incidencias.length()").value(5))
                .andExpect(jsonPath("$.incidencias[0].tipo").value("DANADO"))
                .andExpect(jsonPath("$.incidencias[0].producto.sku").value("TOR-01"))
                .andExpect(jsonPath("$.incidencias[3].descripcion").value("Llegó una arandela")));
        assertThat(recepcion.get("unidades").asInt()).isEqualTo(12);
        // Solo lo aceptado entra al inventario; los dañados y faltantes siguen pendientes.
        assertThat(existencias(tornillo)).isEqualTo(6);
        assertThat(existencias(tuerca)).isEqualTo(6);
        getJson(ORDENES + "/" + id, adminToken)
                .andExpect(jsonPath("$.estado").value("RECIBIDA_PARCIALMENTE"))
                .andExpect(jsonPath("$.detalles[0].pendiente").value(4))
                .andExpect(jsonPath("$.detalles[1].pendiente").value(0));
        assertThat(contar("SELECT COUNT(*) FROM movimientos_inventario")).isEqualTo(2);

        // Supervisión: recepciones con incidencias y entrega que necesita intervención.
        getJson("/api/v1/recepciones?conIncidencias=true", adminToken).andExpect(jsonPath("$.page.totalElements").value(1));
        getJson("/api/v1/recepciones?conIncidencias=false", adminToken).andExpect(jsonPath("$.page.totalElements").value(0));
        getJson("/api/v1/entregas?vista=INTERVENCION", adminToken)
                .andExpect(jsonPath("$.content[0].incidencias").value(5))
                .andExpect(jsonPath("$.content[0].alertas[1]").value("INCIDENCIAS"));
    }

    @Test
    void lasIncidenciasRespetanLoPendienteYSusDatosObligatorios() throws Exception {
        JsonNode orden = ordenEmitida(proveedor, tornillo, 5);
        long id = orden.get("id").asLong();
        long linea = orden.at("/detalles/0/id").asLong();
        long lineaAjena = ordenEmitida(proveedor, tuerca, 5).at("/detalles/0/id").asLong();

        // Aceptado + dañado + faltante > pendiente.
        recibirConIncidencias(id, usuarioToken, clave(), List.<long[]>of(new long[] {linea, 3}),
                List.of(Map.of("tipo", "DANADO", "detalleOrdenId", linea, "cantidad", 2),
                        Map.of("tipo", "FALTANTE", "detalleOrdenId", linea, "cantidad", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['incidencias[1].cantidad']").exists());
        recibirConIncidencias(id, usuarioToken, clave(), List.of(),
                List.of(Map.of("tipo", "DANADO", "cantidad", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['incidencias[0].detalleOrdenId']").exists());
        recibirConIncidencias(id, usuarioToken, clave(), List.of(),
                List.of(Map.of("tipo", "DANADO", "detalleOrdenId", lineaAjena, "cantidad", 1)))
                .andExpect(status().isBadRequest());
        recibirConIncidencias(id, usuarioToken, clave(), List.of(), List.of(Map.of("tipo", "OTRO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['incidencias[0].descripcion']").exists());
        recibirConIncidencias(id, usuarioToken, clave(), List.of(),
                List.of(Map.of("tipo", "DANADO", "detalleOrdenId", linea, "cantidad", 1),
                        Map.of("tipo", "DANADO", "detalleOrdenId", linea, "cantidad", 1)))
                .andExpect(status().isBadRequest());
        // Ni líneas ni incidencias.
        recibirConIncidencias(id, usuarioToken, clave(), List.of(), List.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.detalles").exists());
        // Recibir de más sigue sin regla aprobada: se rechaza (se informa como excedente).
        recibir(id, usuarioToken, new long[] {linea, 6}).andExpect(status().isBadRequest());
        assertThat(contar("SELECT COUNT(*) FROM recepciones")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM incidencias_recepcion")).isZero();

        // Una entrega con solo incidencias (todo llegó dañado): no mueve inventario ni cambia el estado.
        recibirConIncidencias(id, usuarioToken, clave(), List.of(),
                List.of(Map.of("tipo", "DANADO", "detalleOrdenId", linea, "cantidad", 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unidades").value(0));
        getJson(ORDENES + "/" + id, adminToken).andExpect(jsonPath("$.estado").value("EMITIDA"))
                .andExpect(jsonPath("$.detalles[0].pendiente").value(5));
        assertThat(existencias(tornillo)).isZero();
        assertThat(contar("SELECT COUNT(*) FROM movimientos_inventario")).isZero();
    }

    @Test
    void reintentosConLaMismaClaveNoDuplicanNadaYConOtrosDatosSeRechazan() throws Exception {
        JsonNode orden = ordenEmitida(proveedor, tornillo, 10);
        long id = orden.get("id").asLong();
        long linea = orden.at("/detalles/0/id").asLong();
        String clave = clave();
        List<Map<String, Object>> incidencias = List.of(Map.of("tipo", "DANADO", "detalleOrdenId", linea, "cantidad", 1));

        recibirConIncidencias(id, usuarioToken, clave, List.<long[]>of(new long[] {linea, 4}), incidencias)
                .andExpect(status().isCreated());
        recibirConIncidencias(id, usuarioToken, clave, List.<long[]>of(new long[] {linea, 4}), incidencias)
                .andExpect(status().isOk());
        recibirConIncidencias(id, usuarioToken, clave, List.<long[]>of(new long[] {linea, 4}),
                List.of(Map.of("tipo", "DANADO", "detalleOrdenId", linea, "cantidad", 2)))
                .andExpect(status().isConflict());
        assertThat(contar("SELECT COUNT(*) FROM recepciones")).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM incidencias_recepcion")).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM movimientos_inventario")).isEqualTo(1);
        assertThat(existencias(tornillo)).isEqualTo(4);
    }

    @Test
    void trabajadorYAdministradorAlaVezNoRecibenDosVecesLoPendiente() throws Exception {
        for (int ronda = 0; ronda < 3; ronda++) {
            JsonNode orden = ordenEmitida(proveedor, tornillo, 8);
            long id = orden.get("id").asLong();
            long linea = orden.at("/detalles/0/id").asLong();
            List<Integer> estados = enParalelo(4, i -> () -> recibir(id, i % 2 == 0 ? usuarioToken : adminToken,
                    new long[] {linea, 8}));
            assertThat(estados).as("ronda %d", ronda).containsOnlyOnce(201);
            assertThat(estados).filteredOn(s -> s != 201).isSubsetOf(400, 409);
            getJson(ORDENES + "/" + id, adminToken).andExpect(jsonPath("$.estado").value("RECIBIDA"))
                    .andExpect(jsonPath("$.detalles[0].cantidadRecibida").value(8));
        }
        assertThat(existencias(tornillo)).isEqualTo(24);
        assertThat(contar("SELECT SUM(cantidad) FROM movimientos_inventario WHERE producto_id = ?", tornillo)).isEqualTo(24);
    }

    @Test
    void cadaMovimientoSeRastreaHastaSuRecepcionYOrdenYNadaSeEditaDespues() throws Exception {
        JsonNode orden = ordenEmitida(proveedor, tornillo, 5);
        long id = orden.get("id").asLong();
        long linea = orden.at("/detalles/0/id").asLong();
        long recepcion = leer(recibir(id, usuarioToken, new long[] {linea, 2})).get("id").asLong();
        recibir(id, usuarioToken, new long[] {linea, 3}).andExpect(status().isCreated());

        List<String> traza = jdbcTemplate.queryForList("""
                SELECT r.orden_id || ':' || r.id || ':' || m.cantidad || ':' || m.saldo_resultante
                FROM movimientos_inventario m
                JOIN detalles_recepcion d ON d.id = m.detalle_recepcion_id
                JOIN recepciones r ON r.id = d.recepcion_id
                ORDER BY m.id""", String.class);
        assertThat(traza).hasSize(2);
        assertThat(traza.get(0)).isEqualTo(id + ":" + recepcion + ":2:2");
        assertThat(traza.get(1)).startsWith(id + ":").endsWith(":3:5");

        // Una recepción confirmada no se edita ni se borra por la API (no hay operación para ello).
        putJson("/api/v1/recepciones/" + recepcion, adminToken, "{}").andExpect(status().isMethodNotAllowed());
        deleteJson("/api/v1/recepciones/" + recepcion, adminToken).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void otraEmpresaNiVeNiRecibeMercanciaAjena() throws Exception {
        JsonNode orden = ordenEmitida(proveedor, tornillo, 5);
        long id = orden.get("id").asLong();
        long linea = orden.at("/detalles/0/id").asLong();
        long recepcion = leer(recibir(id, usuarioToken, new long[] {linea, 1})).get("id").asLong();

        Empresa norte = crearEmpresa("Norte", "900999999-9");
        String trabajadorNorte = crearUsuarioYEntrar("ana.norte", Rol.TRABAJADOR, norte);
        recibir(id, trabajadorNorte, new long[] {linea, 1}).andExpect(status().isNotFound());
        getJson("/api/v1/recepciones/" + recepcion, trabajadorNorte).andExpect(status().isNotFound());
        getJson("/api/v1/recepciones/mias", trabajadorNorte).andExpect(jsonPath("$.page.totalElements").value(0));
        assertThat(existencias(tornillo)).isEqualTo(1);
    }

    private static String clave() {
        return UUID.randomUUID().toString();
    }
}
