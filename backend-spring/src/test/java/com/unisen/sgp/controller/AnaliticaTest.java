package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.support.ComprasApiTest;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Analítica (Entrega A): cifras reales agregadas en la BD, aislamiento por empresa, modo global
 * explícito para la plataforma, validación del período y días contados en hora de Colombia.
 */
class AnaliticaTest extends ComprasApiTest {

    private static final String GLOBAL = "/api/v1/analitica/global";
    private static final String EMPRESA = "/api/v1/analitica/empresa";
    private static final String PERSONAL = "/api/v1/analitica/personal";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private String superAdmin;
    private Empresa otra;
    private String adminOtra;
    private long proveedor;
    private long tornillo;
    private long tuerca;

    @BeforeEach
    void prepararDatos() throws Exception {
        superAdmin = crearUsuarioYEntrar("plataforma", Rol.SUPER_ADMIN, null);
        otra = crearEmpresa("Ferretería Norte", "800000002-2");
        adminOtra = crearUsuarioYEntrar("admin.norte", Rol.ADMIN_EMPRESA, otra);
        proveedor = crearProveedor("900111111-1", "Aceros Andinos");
        tornillo = crearProducto("TOR-01", "Tornillo", "1000", proveedor);
        tuerca = crearProducto("TUE-01", "Tuerca", "500", proveedor);
    }

    // ---------------------------------------------------------------- autorización

    @Test
    void laAnaliticaGlobalEsSoloDelSuperAdminYEnModoGlobalExplicito() throws Exception {
        getJson(GLOBAL, adminToken).andExpect(status().isForbidden());
        getJson(GLOBAL, usuarioToken).andExpect(status().isForbidden());
        // Sin cabecera: el filtro exige elegir contexto.
        getJson(GLOBAL, superAdmin).andExpect(status().isBadRequest());
        // Dentro de una empresa: se rechaza en vez de devolver solo esa empresa como si fuera "global".
        getEnEmpresa(GLOBAL, superAdmin, empresa.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Modo global requerido"));
        getGlobal(GLOBAL, superAdmin).andExpect(status().isOk());
        getGlobal(GLOBAL + "/empresas", superAdmin).andExpect(status().isOk());
        getEnEmpresa(GLOBAL + "/empresas", superAdmin, empresa.getId()).andExpect(status().isBadRequest());
    }

    @Test
    void laAnaliticaDeEmpresaEsDeGestionYPersonalDeSusMiembros() throws Exception {
        getJson(EMPRESA, usuarioToken).andExpect(status().isForbidden());
        getJson(EMPRESA, adminToken).andExpect(status().isOk());
        // El SUPER_ADMIN la consulta supervisando una empresa, nunca en modo global.
        getEnEmpresa(EMPRESA, superAdmin, empresa.getId()).andExpect(status().isOk());
        getGlobal(EMPRESA, superAdmin).andExpect(status().isBadRequest());
        getJson(PERSONAL, usuarioToken).andExpect(status().isOk());
        getJson(PERSONAL, adminToken).andExpect(status().isOk());
        getEnEmpresa(PERSONAL, superAdmin, empresa.getId()).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- período

    @Test
    void validaElPeriodoYEligeLaGranularidad() throws Exception {
        LocalDate hoy = LocalDate.now(BOGOTA);
        getJson(EMPRESA + "?desde=" + hoy + "&hasta=" + hoy.minusDays(1), adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.desde").exists());
        getJson(EMPRESA + "?hasta=" + hoy.plusDays(1), adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.hasta").exists());
        getJson(EMPRESA + "?desde=" + hoy.minusDays(366), adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.desde").value("El período no puede superar 366 días."));
        getJson(EMPRESA + "?desde=16-10-2026", adminToken).andExpect(status().isBadRequest());

        // Por defecto, los últimos 30 días, por día y con la zona del negocio.
        JsonNode porDefecto = leer(getJson(EMPRESA, adminToken).andExpect(status().isOk()));
        assertThat(porDefecto.at("/periodo/hasta").asText()).isEqualTo(hoy.toString());
        assertThat(porDefecto.at("/periodo/desde").asText()).isEqualTo(hoy.minusDays(29).toString());
        assertThat(porDefecto.at("/periodo/zonaHoraria").asText()).isEqualTo("America/Bogota");
        assertThat(porDefecto.at("/periodo/granularidad").asText()).isEqualTo("DIA");
        assertThat(porDefecto.at("/recepciones/evolucion")).hasSize(30);

        // Un año: por mes (uno por cada mes tocado por el período).
        JsonNode anual = leer(getJson(EMPRESA + "?desde=" + hoy.minusDays(365), adminToken).andExpect(status().isOk()));
        assertThat(anual.at("/periodo/granularidad").asText()).isEqualTo("MES");
        assertThat(anual.at("/recepciones/evolucion").size()).isBetween(12, 13);
        assertThat(anual.at("/recepciones/evolucion/0/fecha").asText()).endsWith("-01");
    }

    // ---------------------------------------------------------------- sin datos vs. ceros

    @Test
    void sinActividadDevuelveCerosRealesYElTotalHistoricoPermiteDistinguirlos() throws Exception {
        JsonNode global = leer(getGlobal(GLOBAL, superAdmin).andExpect(status().isOk()));
        assertThat(global.at("/empresas/total").asLong()).isEqualTo(2);
        assertThat(global.at("/empresas/activas").asLong()).isEqualTo(2);
        assertThat(global.at("/empresas/inactivas").asLong()).isZero();
        assertThat(global.at("/ordenes/totalHistorico").asLong()).isZero();
        assertThat(global.at("/ordenes/porEstado/BORRADOR").asLong()).isZero();
        assertThat(global.at("/topEmpresas")).isEmpty();
        assertThat(global.at("/evolucionOrdenes")).hasSize(30);
        global.at("/evolucionOrdenes").forEach(punto -> assertThat(punto.get("valor").asLong()).isZero());

        JsonNode deEmpresa = leer(getJson(EMPRESA, adminToken).andExpect(status().isOk()));
        assertThat(deEmpresa.at("/compras/totalHistorico").asLong()).isZero();
        assertThat(deEmpresa.at("/compras/valorComprometido").decimalValue()).isZero();
        assertThat(deEmpresa.at("/recepciones/totalHistorico").asLong()).isZero();
        // Los dos productos recién creados: activos y sin existencias.
        assertThat(deEmpresa.at("/inventario/productosActivos").asLong()).isEqualTo(2);
        assertThat(deEmpresa.at("/inventario/agotados").asLong()).isEqualTo(2);
        assertThat(deEmpresa.at("/inventario/disponibles").asLong()).isZero();
    }

    // ---------------------------------------------------------------- cifras reales y aislamiento

    @Test
    void laEmpresaSoloCuentaSusDatosYLaPlataformaLosDeTodas() throws Exception {
        // Unisen: una orden emitida (10 × 1000) y recibida en parte (4), otra cancelada tras emitirla
        // y un borrador. Stock mínimo de la tuerca: 5, con 3 unidades → crítica.
        JsonNode emitida = ordenEmitida(proveedor, tornillo, 10);
        recibir(emitida.get("id").asLong(), adminToken, new long[] {emitida.at("/detalles/0/id").asLong(), 4})
                .andExpect(status().isCreated());
        JsonNode cancelada = ordenEmitida(proveedor, tuerca, 7);
        cancelar(cancelada.get("id").asLong(), adminToken, "Precio desactualizado").andExpect(status().isOk());
        crearOrden(adminToken, proveedor, new long[] {tuerca, 2});
        putJson("/api/v1/productos/" + tuerca, adminToken, """
                {"sku": "TUE-01", "nombre": "Tuerca", "precio": 500, "stockMinimo": 5, "proveedorId": %d}
                """.formatted(proveedor)).andExpect(status().isOk());
        ajustar(adminToken, tuerca, 3, "Inventario inicial").andExpect(status().isCreated());
        postJson("/api/v1/solicitudes", usuarioToken, json(Map.of("justificacion", "Reposición",
                "detalles", List.of(Map.of("productoId", tornillo, "cantidad", 2))))).andExpect(status().isCreated());

        // Norte: dos órdenes emitidas de mayor valor, que Unisen no debe ver.
        long proveedorNorte = leer(postJson("/api/v1/proveedores", adminOtra,
                "{\"nit\": \"900333333-3\", \"razonSocial\": \"Norteños\", \"email\": \"n@n.com\"}")).get("id").asLong();
        long productoNorte = leer(postJson("/api/v1/productos", adminOtra, """
                {"sku": "NOR-01", "nombre": "Cable", "precio": 9000, "proveedorId": %d}
                """.formatted(proveedorNorte))).get("id").asLong();
        for (int i = 0; i < 2; i++) {
            long id = crearOrden(adminOtra, proveedorNorte, new long[] {productoNorte, 100}).get("id").asLong();
            emitir(id, adminOtra).andExpect(status().isOk());
        }

        JsonNode unisen = leer(getJson(EMPRESA, adminToken).andExpect(status().isOk()));
        assertThat(unisen.at("/compras/ordenesEmitidas").asLong()).isEqualTo(2);
        // El valor comprometido excluye la orden cancelada: solo 10 × 1000.
        assertThat(unisen.at("/compras/valorComprometido").decimalValue()).isEqualByComparingTo("10000");
        assertThat(unisen.at("/compras/valorRecibido").decimalValue()).isEqualByComparingTo("4000");
        assertThat(unisen.at("/compras/totalHistorico").asLong()).isEqualTo(3);
        assertThat(unisen.at("/compras/ordenesPorEstado/BORRADOR").asLong()).isEqualTo(1);
        assertThat(unisen.at("/compras/ordenesPorEstado/RECIBIDA_PARCIALMENTE").asLong()).isEqualTo(1);
        assertThat(unisen.at("/compras/ordenesPorEstado/CANCELADA").asLong()).isEqualTo(1);
        assertThat(unisen.at("/compras/ordenesPorEstado/EMITIDA").asLong()).isZero();
        assertThat(unisen.at("/recepciones/enPeriodo").asLong()).isEqualTo(1);
        assertThat(unisen.at("/recepciones/unidades").asLong()).isEqualTo(4);
        LocalDate hoy = LocalDate.now(BOGOTA);
        JsonNode ultimoDia = unisen.at("/recepciones/evolucion").get(29);
        assertThat(ultimoDia.get("fecha").asText()).isEqualTo(hoy.toString());
        assertThat(ultimoDia.get("valor").asLong()).isEqualTo(1);
        assertThat(unisen.at("/inventario/productosActivos").asLong()).isEqualTo(2);
        assertThat(unisen.at("/inventario/disponibles").asLong()).isEqualTo(2);
        assertThat(unisen.at("/inventario/criticos").asLong()).isEqualTo(1);
        assertThat(unisen.at("/inventario/agotados").asLong()).isZero();
        assertThat(unisen.at("/inventario/requierenAtencion/0/sku").asText()).isEqualTo("TUE-01");
        assertThat(unisen.at("/inventario/requierenAtencion/0/estado").asText()).isEqualTo("CRITICO");
        assertThat(unisen.at("/solicitudesPorEstado/PENDIENTE").asLong()).isEqualTo(1);

        JsonNode norte = leer(getJson(EMPRESA, adminOtra).andExpect(status().isOk()));
        assertThat(norte.at("/compras/ordenesEmitidas").asLong()).isEqualTo(2);
        assertThat(norte.at("/compras/valorComprometido").decimalValue()).isEqualByComparingTo("1800000");
        assertThat(norte.at("/recepciones/totalHistorico").asLong()).isZero();
        assertThat(norte.at("/solicitudesPorEstado/PENDIENTE").asLong()).isZero();

        // El SUPER_ADMIN supervisando Unisen ve exactamente lo mismo que su administrador.
        JsonNode supervision = leer(getEnEmpresa(EMPRESA, superAdmin, empresa.getId()).andExpect(status().isOk()));
        assertThat(supervision.get("compras")).isEqualTo(unisen.get("compras"));

        // Plataforma: todas las empresas; Norte primero (más órdenes en el período).
        JsonNode global = leer(getGlobal(GLOBAL, superAdmin).andExpect(status().isOk()));
        assertThat(global.at("/ordenes/emitidasEnPeriodo").asLong()).isEqualTo(4);
        assertThat(global.at("/ordenes/totalHistorico").asLong()).isEqualTo(5);
        assertThat(global.at("/topEmpresas/0/nombre").asText()).isEqualTo("Ferretería Norte");
        assertThat(global.at("/topEmpresas/0/ordenes").asLong()).isEqualTo(2);
        assertThat(global.at("/topEmpresas/1/nombre").asText()).isEqualTo("Unisen");
        assertThat(global.at("/topEmpresas/1/valorComprometido").decimalValue()).isEqualByComparingTo("10000");
        assertThat(global.at("/evolucionOrdenes").get(29).get("valor").asLong()).isEqualTo(4);

        JsonNode porEmpresa = leer(getGlobal(GLOBAL + "/empresas", superAdmin).andExpect(status().isOk()));
        assertThat(porEmpresa.at("/page/totalElements").asLong()).isEqualTo(2);
        assertThat(porEmpresa.at("/content/0/codigo").asText()).isEqualTo(otra.getCodigo());
        assertThat(porEmpresa.at("/content/1/ordenesEnPeriodo").asLong()).isEqualTo(2);
        // Abiertas ahora en Unisen: el borrador y la parcialmente recibida.
        assertThat(porEmpresa.at("/content/1/ordenesAbiertas").asLong()).isEqualTo(2);
    }

    @Test
    void elTrabajadorVeSusSolicitudesYLaDisponibilidadSinVentas() throws Exception {
        ajustar(adminToken, tornillo, 8, "Inventario inicial").andExpect(status().isCreated());
        postJson("/api/v1/solicitudes", usuarioToken, json(Map.of("justificacion", "Reposición del trabajador",
                "detalles", List.of(Map.of("productoId", tornillo, "cantidad", 1))))).andExpect(status().isCreated());
        postJson("/api/v1/solicitudes", adminToken, json(Map.of("justificacion", "Del administrador",
                "detalles", List.of(Map.of("productoId", tuerca, "cantidad", 1))))).andExpect(status().isCreated());

        JsonNode personal = leer(getJson(PERSONAL, usuarioToken).andExpect(status().isOk()));
        assertThat(personal.get("productosDisponibles").asLong()).isEqualTo(1);
        assertThat(personal.get("productosAgotados").asLong()).isEqualTo(1);
        assertThat(personal.at("/misSolicitudes/PENDIENTE").asLong()).isEqualTo(1);
        assertThat(personal.has("ventas")).isFalse();
        // Otra empresa no ve nada de esto.
        String trabajadorNorte = crearUsuarioYEntrar("trabajador.norte", Rol.TRABAJADOR, otra);
        JsonNode norte = leer(getJson(PERSONAL, trabajadorNorte).andExpect(status().isOk()));
        assertThat(norte.get("productosDisponibles").asLong()).isZero();
        assertThat(norte.at("/misSolicitudes/PENDIENTE").asLong()).isZero();
    }

    // ---------------------------------------------------------------- días de Colombia

    @Test
    void cuentaCadaOrdenEnSuDiaDeColombiaNoEnElDeUtc() throws Exception {
        LocalDate dia = LocalDate.now(BOGOTA).minusDays(10);
        // 22:00 en Bogotá = 03:00 UTC del día siguiente: debe contar en «dia».
        fijarEmision(ordenEmitida(proveedor, tornillo, 1), dia.atTime(LocalTime.of(22, 0)));
        // 00:30 en Bogotá del día siguiente = 05:30 UTC: cuenta en el día siguiente.
        fijarEmision(ordenEmitida(proveedor, tornillo, 1), dia.plusDays(1).atTime(LocalTime.of(0, 30)));
        // 23:59:59 del día anterior al período: fuera.
        fijarEmision(ordenEmitida(proveedor, tornillo, 1), dia.minusDays(1).atTime(LocalTime.of(23, 59, 59)));

        JsonNode respuesta = leer(getGlobal(GLOBAL + "?desde=" + dia + "&hasta=" + dia.plusDays(1), superAdmin)
                .andExpect(status().isOk()));
        assertThat(respuesta.at("/ordenes/emitidasEnPeriodo").asLong()).isEqualTo(2);
        assertThat(respuesta.at("/evolucionOrdenes")).hasSize(2);
        assertThat(respuesta.at("/evolucionOrdenes/0/fecha").asText()).isEqualTo(dia.toString());
        assertThat(respuesta.at("/evolucionOrdenes/0/valor").asLong()).isEqualTo(1);
        assertThat(respuesta.at("/evolucionOrdenes/1/valor").asLong()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- sin N+1

    @Test
    void elNumeroDeConsultasNoDependeDelVolumenDeDatos() throws Exception {
        long conPocos = consultasDe(() -> getJson(EMPRESA, adminToken).andExpect(status().isOk()));
        long globalConPocos = consultasDe(() -> getGlobal(GLOBAL, superAdmin).andExpect(status().isOk()));
        for (int i = 0; i < 6; i++) {
            long producto = crearProducto("EXTRA-" + i, "Extra " + i, "100", proveedor);
            JsonNode orden = ordenEmitida(proveedor, producto, 5);
            recibir(orden.get("id").asLong(), adminToken, new long[] {orden.at("/detalles/0/id").asLong(), 2});
        }
        assertThat(consultasDe(() -> getJson(EMPRESA, adminToken).andExpect(status().isOk()))).isEqualTo(conPocos);
        assertThat(consultasDe(() -> getGlobal(GLOBAL, superAdmin).andExpect(status().isOk())))
                .isEqualTo(globalConPocos);
    }

    private long consultasDe(ResultActionsSupplier peticion) throws Exception {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();
        peticion.get();
        return estadisticas.getPrepareStatementCount();
    }

    private void fijarEmision(JsonNode orden, LocalDateTime enBogota) {
        jdbcTemplate.update("UPDATE ordenes_compra SET emitida_en = ? WHERE id = ?",
                enBogota.atZone(BOGOTA).toOffsetDateTime(), orden.get("id").asLong());
    }
}
