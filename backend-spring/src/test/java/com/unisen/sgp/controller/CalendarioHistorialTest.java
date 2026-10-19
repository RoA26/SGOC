package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.support.ComprasApiTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Calendario operativo (eventos derivados, fechas sin horas inventadas, roles y aislamiento) e
 * historial completo de una orden: de la solicitud a la recepción final con su kárdex.
 */
class CalendarioHistorialTest extends ComprasApiTest {

    private static final String CALENDARIO = "/api/v1/calendario";

    private long proveedor;
    private long tornillo;

    @BeforeEach
    void prepararCatalogo() throws Exception {
        proveedor = crearProveedor("900111111-1", "Aceros Andinos");
        tornillo = crearProducto("TOR-01", "Tornillo", "1000", proveedor);
    }

    @Test
    void elCalendarioDerivaLosEventosConLaFechaQueRepresentaCadaUno() throws Exception {
        JsonNode conFranja = ordenEmitida(proveedor, tornillo, 10);
        long idFranja = conFranja.get("id").asLong();
        programar(idFranja, adminToken, 2, Map.of("horaDesde", "08:00", "horaHasta", "10:30", "confirmadaProveedor", true))
                .andExpect(status().isOk());
        long sinFranja = ordenEmitida(proveedor, tornillo, 4).get("id").asLong();
        programar(sinFranja, adminToken, 0, Map.of()).andExpect(status().isOk());
        moverFechaEstimada(sinFranja, -1);
        recibir(idFranja, usuarioToken, new long[] {conFranja.at("/detalles/0/id").asLong(), 3}).andExpect(status().isCreated());
        postJson("/api/v1/solicitudes", usuarioToken, json(Map.of("justificacion", "Reposición de tornillería",
                "detalles", List.of(Map.of("productoId", tornillo, "cantidad", 2))))).andExpect(status().isCreated());

        JsonNode calendario = leer(getJson(CALENDARIO + "?desde=" + hoy().minusDays(3) + "&hasta=" + hoy().plusDays(5),
                adminToken).andExpect(status().isOk()));
        assertThat(calendario.get("zonaHoraria").asText()).isEqualTo("America/Bogota");
        assertThat(calendario.get("hoy").asText()).isEqualTo(hoy().toString());
        List<JsonNode> eventos = lista(calendario.get("eventos"));

        JsonNode entregaFranja = buscar(eventos, "entrega-" + idFranja);
        assertThat(entregaFranja.get("fecha").asText()).isEqualTo(hoy().plusDays(2).toString());
        assertThat(entregaFranja.get("todoElDia").asBoolean()).isFalse();
        assertThat(entregaFranja.get("horaInicio").asText()).isEqualTo("08:00");
        assertThat(entregaFranja.get("horaFin").asText()).isEqualTo("10:30");
        assertThat(entregaFranja.get("estado").asText()).isEqualTo("CONFIRMADA");
        assertThat(entregaFranja.get("parcial").asBoolean()).isTrue();

        // Sin franja: todo el día, sin hora inventada. Vencida con pendiente: retrasada.
        JsonNode entregaDia = buscar(eventos, "entrega-" + sinFranja);
        assertThat(entregaDia.get("todoElDia").asBoolean()).isTrue();
        assertThat(entregaDia.has("horaInicio")).isFalse();
        assertThat(entregaDia.get("fecha").asText()).isEqualTo(hoy().minusDays(1).toString());
        assertThat(entregaDia.get("estado").asText()).isEqualTo("RETRASADA");

        // Emisión y recepción: su instante real, con hora.
        JsonNode emision = buscar(eventos, "emision-" + idFranja);
        assertThat(emision.get("tipo").asText()).isEqualTo("ORDEN_EMITIDA");
        assertThat(emision.get("todoElDia").asBoolean()).isFalse();
        assertThat(emision.has("instante")).isTrue();
        JsonNode recepcion = eventos.stream().filter(e -> e.get("tipo").asText().equals("RECEPCION")).findFirst().orElseThrow();
        assertThat(recepcion.get("estado").asText()).isEqualTo("PARCIAL");
        assertThat(recepcion.get("fecha").asText()).isEqualTo(hoy().toString());
        assertThat(eventos).anyMatch(e -> e.get("tipo").asText().equals("SOLICITUD_PENDIENTE"));
        // Nunca costos.
        assertThat(calendario.toString()).doesNotContain("costo").doesNotContain("valor");

        // Filtro por tipo.
        JsonNode soloEntregas = leer(getJson(CALENDARIO + "?desde=" + hoy().minusDays(3) + "&hasta=" + hoy().plusDays(5)
                + "&tipos=ENTREGA", adminToken));
        assertThat(lista(soloEntregas.get("eventos"))).allMatch(e -> e.get("tipo").asText().equals("ENTREGA")).hasSize(2);

        // Al reprogramar, el calendario muestra la fecha vigente (y una sola vez).
        programar(idFranja, adminToken, 4, Map.of("motivo", "Retraso del transportador")).andExpect(status().isOk());
        List<JsonNode> despues = lista(leer(getJson(CALENDARIO + "?desde=" + hoy().minusDays(3) + "&hasta="
                + hoy().plusDays(5) + "&tipos=ENTREGA", adminToken)).get("eventos"));
        assertThat(despues).filteredOn(e -> e.get("id").asText().equals("entrega-" + idFranja)).hasSize(1)
                .first().satisfies(e -> assertThat(e.get("fecha").asText()).isEqualTo(hoy().plusDays(4).toString()));
    }

    @Test
    void laRecepcionQueCompletaLaOrdenSeMarcaComoCompleta() throws Exception {
        JsonNode orden = ordenEmitida(proveedor, tornillo, 5);
        long id = orden.get("id").asLong();
        long linea = orden.at("/detalles/0/id").asLong();
        recibir(id, usuarioToken, new long[] {linea, 2}).andExpect(status().isCreated());
        recibir(id, usuarioToken, new long[] {linea, 3}).andExpect(status().isCreated());
        List<JsonNode> recepciones = lista(leer(getJson(CALENDARIO + "?desde=" + hoy() + "&hasta=" + hoy()
                + "&tipos=RECEPCION", usuarioToken)).get("eventos"));
        assertThat(recepciones).extracting(e -> e.get("estado").asText()).containsExactly("PARCIAL", "COMPLETA");
    }

    @Test
    void cadaRolVeLoSuyoYNuncaOtraEmpresa() throws Exception {
        long id = ordenEmitida(proveedor, tornillo, 5).get("id").asLong();
        programar(id, adminToken, 1, Map.of()).andExpect(status().isOk());
        postJson("/api/v1/solicitudes", adminToken, json(Map.of("justificacion", "Del administrador",
                "detalles", List.of(Map.of("productoId", tornillo, "cantidad", 1))))).andExpect(status().isCreated());
        String rango = "?desde=" + hoy().minusDays(1) + "&hasta=" + hoy().plusDays(2);

        // Trabajador: entregas sí; emisiones no; solicitudes ajenas no.
        List<JsonNode> trabajador = lista(leer(getJson(CALENDARIO + rango, usuarioToken)).get("eventos"));
        assertThat(trabajador).extracting(e -> e.get("tipo").asText()).containsExactly("ENTREGA");

        Empresa norte = crearEmpresa("Norte", "900999999-9");
        String adminNorte = crearUsuarioYEntrar("admin.norte", Rol.ADMIN_EMPRESA, norte);
        assertThat(lista(leer(getJson(CALENDARIO + rango, adminNorte)).get("eventos"))).isEmpty();

        String plataforma = crearUsuarioYEntrar("plataforma", Rol.SUPER_ADMIN, null);
        getGlobal(CALENDARIO + rango, plataforma).andExpect(status().isBadRequest());
        assertThat(lista(leer(getEnEmpresa(CALENDARIO + rango, plataforma, empresa.getId())).get("eventos")))
                .extracting(e -> e.get("tipo").asText()).contains("ENTREGA", "ORDEN_EMITIDA", "SOLICITUD_PENDIENTE");

        // Período obligatorio, coherente y acotado.
        getJson(CALENDARIO, adminToken).andExpect(status().isBadRequest());
        getJson(CALENDARIO + "?desde=" + hoy() + "&hasta=" + hoy().minusDays(1), adminToken).andExpect(status().isBadRequest());
        getJson(CALENDARIO + "?desde=" + hoy() + "&hasta=" + hoy().plusDays(70), adminToken).andExpect(status().isBadRequest());
    }

    @Test
    void elHistorialReconstruyeLaSecuenciaCompletaDeLaOrden() throws Exception {
        long solicitud = leer(postJson("/api/v1/solicitudes", usuarioToken, json(Map.of("justificacion", "Obra norte",
                "detalles", List.of(Map.of("productoId", tornillo, "cantidad", 10)))))).get("id").asLong();
        patchJson("/api/v1/solicitudes/" + solicitud + "/estado", adminToken,
                json(Map.of("estado", "APROBADA", "comentario", "Adelante"))).andExpect(status().isOk());
        JsonNode borrador = leer(postJson(ORDENES + "/desde-solicitud/" + solicitud, adminToken, "")).get(0);
        long id = borrador.get("id").asLong();
        long linea = borrador.at("/detalles/0/id").asLong();
        emitir(id, adminToken).andExpect(status().isOk());
        programar(id, adminToken, 2, Map.of()).andExpect(status().isOk());
        programar(id, adminToken, 3, Map.of("motivo", "Festivo")).andExpect(status().isOk());
        recibirConIncidencias(id, usuarioToken, UUID.randomUUID().toString(), List.<long[]>of(new long[] {linea, 6}),
                List.of(Map.of("tipo", "FALTANTE", "detalleOrdenId", linea, "cantidad", 4))).andExpect(status().isCreated());
        recibir(id, adminToken, new long[] {linea, 4}).andExpect(status().isCreated());

        JsonNode historial = leer(getJson(ORDENES + "/" + id + "/historial", adminToken).andExpect(status().isOk()));
        assertThat(lista(historial)).extracting(e -> e.get("tipo").asText()).containsExactly(
                "SOLICITUD_CREADA", "SOLICITUD_APROBADA", "ORDEN_CREADA", "ORDEN_EMITIDA", "DOCUMENTO_GENERADO",
                "ENTREGA_PROGRAMADA", "ENTREGA_REPROGRAMADA", "RECEPCION_PARCIAL", "RECEPCION_FINAL");
        JsonNode parcial = historial.get(7);
        assertThat(parcial.at("/usuario/username").asText()).isEqualTo("compras");
        assertThat(parcial.at("/recepcion/incidencias/0/tipo").asText()).isEqualTo("FALTANTE");
        assertThat(parcial.at("/movimientos/0/cantidad").asInt()).isEqualTo(6);
        assertThat(parcial.at("/movimientos/0/saldoResultante").asInt()).isEqualTo(6);
        JsonNode final_ = historial.get(8);
        assertThat(final_.at("/movimientos/0/saldoResultante").asInt()).isEqualTo(10);
        assertThat(historial.get(6).at("/programacion/motivoCambio").asText()).isEqualTo("Festivo");
        assertThat(historial.get(4).at("/documento/version").asInt()).isEqualTo(1);

        // Solo gestión, y solo de su empresa.
        getJson(ORDENES + "/" + id + "/historial", usuarioToken).andExpect(status().isForbidden());
        String intruso = crearUsuarioYEntrar("admin.norte", Rol.ADMIN_EMPRESA, crearEmpresa("Norte", "900999999-9"));
        getJson(ORDENES + "/" + id + "/historial", intruso).andExpect(status().isNotFound());
    }

    private static List<JsonNode> lista(JsonNode array) {
        List<JsonNode> lista = new ArrayList<>();
        array.forEach(lista::add);
        return lista;
    }

    private static JsonNode buscar(List<JsonNode> eventos, String id) {
        return eventos.stream().filter(e -> e.get("id").asText().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("Falta el evento " + id + " en " + eventos));
    }
}
