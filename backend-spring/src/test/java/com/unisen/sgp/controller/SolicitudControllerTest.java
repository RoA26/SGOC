package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.support.ApiIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class SolicitudControllerTest extends ApiIntegrationTest {

    private static final String URL = "/api/v1/solicitudes";
    private static final String JUSTIFICACION = "Reposición de tornillería para la línea 2.";

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private long tornillo;
    private long tuerca;

    @BeforeEach
    void crearCatalogo() throws Exception {
        long proveedor = crearProveedor("900123456-7", "Aceros Andinos");
        tornillo = crearProducto("TOR-001", "Tornillo hexagonal", "1250", proveedor);
        tuerca = crearProducto("TUE-001", "Tuerca M8", "300", proveedor);
    }

    // ------------------------------------------------------------ helpers

    private static String cuerpo(String justificacion, long[]... lineas) {
        StringBuilder detalles = new StringBuilder();
        for (long[] linea : lineas) {
            if (!detalles.isEmpty()) {
                detalles.append(',');
            }
            detalles.append("{\"productoId\": ").append(linea[0]).append(", \"cantidad\": ").append(linea[1]).append('}');
        }
        return "{\"justificacion\": \"" + justificacion + "\", \"detalles\": [" + detalles + "]}";
    }

    private long crearSolicitud(String token, long[]... lineas) throws Exception {
        return leer(postJson(URL, token, cuerpo(JUSTIFICACION, lineas)).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private String tokenDe(String username, Rol rol) throws Exception {
        return crearUsuarioYEntrar(username, rol, empresa);
    }

    private int contar(String tabla) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
    }

    // --------------------------------------------------------------- crear

    @Test
    void usuarioCreaSolicitudPendienteConTodasSusLineas() throws Exception {
        postJson(URL, usuarioToken, cuerpo(JUSTIFICACION, new long[] {tornillo, 10}, new long[] {tuerca, 4}))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/v1/solicitudes/\\d+")))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.justificacion").value(JUSTIFICACION))
                .andExpect(jsonPath("$.fecha").isNotEmpty())
                .andExpect(jsonPath("$.solicitante.username").value("compras"))
                .andExpect(jsonPath("$.detalles", hasSize(2)))
                .andExpect(jsonPath("$.detalles[0].producto.sku").value("TOR-001"))
                .andExpect(jsonPath("$.detalles[0].producto.activo").value(true))
                .andExpect(jsonPath("$.detalles[0].cantidad").value(10))
                .andExpect(jsonPath("$.detalles[0].subtotalEstimado").value(12500))
                .andExpect(jsonPath("$.detalles[1].producto.nombre").value("Tuerca M8"))
                .andExpect(jsonPath("$.totalEstimado").value(13700))
                .andExpect(jsonPath("$.revisadoPor").doesNotExist())
                .andExpect(jsonPath("$.fechaRevision").doesNotExist());

        assertThat(contar("solicitudes")).isEqualTo(1);
        assertThat(contar("detalles_solicitud")).isEqualTo(2);
    }

    @Test
    void elClienteNoPuedeFijarNiElSolicitanteNiElEstado() throws Exception {
        long adminId = jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE username = 'admin'", Long.class);
        String body = """
                {"justificacion": "%s", "estado": "APROBADA", "usuarioId": %d, "solicitante": {"id": %d},
                 "detalles": [{"productoId": %d, "cantidad": 1}]}
                """.formatted(JUSTIFICACION, adminId, adminId, tornillo);

        postJson(URL, usuarioToken, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.solicitante.username").value("compras"));
    }

    @Test
    void validaCabeceraYLineas() throws Exception {
        postJson(URL, usuarioToken, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.justificacion").value("La justificación es obligatoria."))
                .andExpect(jsonPath("$.errors.detalles").value("Agrega al menos un producto."));

        postJson(URL, usuarioToken, cuerpo("Corta"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.justificacion").value("La justificación debe tener entre 10 y 1000 caracteres."))
                .andExpect(jsonPath("$.errors.detalles").value("Agrega al menos un producto."));

        postJson(URL, usuarioToken, """
                {"justificacion": "%s", "detalles": [{"productoId": %d, "cantidad": 0}, {"cantidad": 2}, null]}
                """.formatted(JUSTIFICACION, tornillo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['detalles[0].cantidad']").value("La cantidad debe ser mayor que 0."))
                .andExpect(jsonPath("$.errors['detalles[1].productoId']").value("Selecciona un producto."))
                .andExpect(jsonPath("$.errors['detalles[2]']").value("La línea está vacía."));

        postJson(URL, usuarioToken, cuerpo(JUSTIFICACION, new long[] {tornillo, 1_000_000}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['detalles[0].cantidad']").value("La cantidad máxima por línea es 999.999."));

        long[][] demasiadas = new long[51][];
        for (int i = 0; i < demasiadas.length; i++) {
            demasiadas[i] = new long[] {tornillo, 1};
        }
        postJson(URL, usuarioToken, cuerpo(JUSTIFICACION, demasiadas))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.detalles").value("La solicitud admite hasta 50 productos."));

        assertThat(contar("solicitudes")).isZero();
    }

    @Test
    void unaCantidadDecimalSeRechazaEnLugarDeTruncarse() throws Exception {
        postJson(URL, usuarioToken, """
                {"justificacion": "%s", "detalles": [{"productoId": %d, "cantidad": 2.5}]}
                """.formatted(JUSTIFICACION, tornillo))
                .andExpect(status().isBadRequest());
        assertThat(contar("solicitudes")).isZero();
    }

    @Test
    void unProductoInexistenteODadoDeBajaAnulaTodaLaSolicitud() throws Exception {
        postJson(URL, usuarioToken, cuerpo(JUSTIFICACION, new long[] {tornillo, 1}, new long[] {999_999, 2}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['detalles[1].productoId']").value("El producto no existe o fue dado de baja."));

        deleteJson("/api/v1/productos/" + tuerca, adminToken).andExpect(status().isNoContent());
        postJson(URL, usuarioToken, cuerpo(JUSTIFICACION, new long[] {tuerca, 1}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['detalles[0].productoId']").value("El producto no existe o fue dado de baja."));

        // Todo o nada: ni la cabecera ni la línea válida quedaron guardadas.
        assertThat(contar("solicitudes")).isZero();
        assertThat(contar("detalles_solicitud")).isZero();
    }

    @Test
    void unProductoRepetidoSeRechazaEnLaLineaDuplicada() throws Exception {
        postJson(URL, usuarioToken, cuerpo(JUSTIFICACION, new long[] {tornillo, 1}, new long[] {tuerca, 1}, new long[] {tornillo, 3}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['detalles[2].productoId']")
                        .value("Este producto ya está en otra línea: ajusta allí la cantidad."));
        assertThat(contar("solicitudes")).isZero();
    }

    @Test
    void crearRequiereAutenticacion() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(cuerpo(JUSTIFICACION, new long[] {tornillo, 1})))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------- lectura por fila

    @Test
    void cadaUsuarioVeSoloSusSolicitudesYLosGestoresTodas() throws Exception {
        String otroToken = tokenDe("otro", Rol.USUARIO);
        String gerenteToken = tokenDe("gerente", Rol.GERENTE);
        long propia = crearSolicitud(usuarioToken, new long[] {tornillo, 1});
        long ajena = crearSolicitud(otroToken, new long[] {tuerca, 2});
        crearSolicitud(adminToken, new long[] {tornillo, 3});

        getJson(URL, usuarioToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(propia))
                .andExpect(jsonPath("$.content[0].detalles[0].producto.sku").value("TOR-001"));
        getJson(URL, otroToken).andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ajena));
        getJson(URL, adminToken).andExpect(jsonPath("$.page.totalElements").value(3));
        getJson(URL, gerenteToken).andExpect(jsonPath("$.page.totalElements").value(3));

        // La ajena no existe para él (404, no 403: no se revela).
        getJson(URL + "/" + ajena, usuarioToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe la solicitud con id " + ajena + "."));
        getJson(URL + "/" + propia, usuarioToken).andExpect(status().isOk());
        getJson(URL + "/" + ajena, gerenteToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solicitante.username").value("otro"));
        getJson(URL + "/999999", adminToken).andExpect(status().isNotFound());
    }

    @Test
    void elListadoSeOrdenaPorFechaDescendenteYSeFiltraPorEstado() throws Exception {
        long primera = crearSolicitud(usuarioToken, new long[] {tornillo, 1});
        long segunda = crearSolicitud(usuarioToken, new long[] {tuerca, 1});
        patchJson(URL + "/" + primera + "/estado", adminToken, "{\"estado\": \"APROBADA\"}").andExpect(status().isOk());

        getJson(URL, usuarioToken)
                .andExpect(jsonPath("$.content[0].id").value(segunda))
                .andExpect(jsonPath("$.content[1].id").value(primera));
        getJson(URL + "?estado=PENDIENTE", usuarioToken)
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(segunda));
        getJson(URL + "?estado=APROBADA", adminToken)
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(primera));
        getJson(URL + "?estado=INVENTADO", adminToken).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ revisión

    @Test
    void unUsuarioNoPuedeCambiarElEstado() throws Exception {
        long id = crearSolicitud(usuarioToken, new long[] {tornillo, 1});
        patchJson(URL + "/" + id + "/estado", usuarioToken, "{\"estado\": \"APROBADA\"}")
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(URL + "/" + id + "/estado")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\": \"APROBADA\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject("SELECT estado FROM solicitudes WHERE id = ?", String.class, id))
                .isEqualTo("PENDIENTE");
    }

    @Test
    void adminApruebaYElEstadoFinalNoCambia() throws Exception {
        long id = crearSolicitud(usuarioToken, new long[] {tornillo, 2});

        patchJson(URL + "/" + id + "/estado", adminToken, "{\"estado\": \"APROBADA\", \"comentario\": \"  Presupuesto OK \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.revisadoPor.username").value("admin"))
                .andExpect(jsonPath("$.fechaRevision").isNotEmpty())
                .andExpect(jsonPath("$.comentarioRevision").value("Presupuesto OK"))
                .andExpect(jsonPath("$.solicitante.username").value("compras"));

        patchJson(URL + "/" + id + "/estado", adminToken, "{\"estado\": \"RECHAZADA\", \"comentario\": \"Tarde\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La solicitud ya fue aprobada; su estado no puede cambiar."));

        // El solicitante ve el resultado de la revisión.
        getJson(URL + "/" + id, usuarioToken)
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.revisadoPor.nombre").value("Admin"));
    }

    @Test
    void gerenteRechazaConMotivoObligatorio() throws Exception {
        String gerenteToken = tokenDe("gerente", Rol.GERENTE);
        long id = crearSolicitud(usuarioToken, new long[] {tuerca, 5});

        patchJson(URL + "/" + id + "/estado", gerenteToken, "{\"estado\": \"RECHAZADA\", \"comentario\": \"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.comentario").value("Indica el motivo del rechazo."));
        patchJson(URL + "/" + id + "/estado", gerenteToken, "{\"estado\": \"RECHAZADA\", \"comentario\": \"Hay stock en bodega.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.revisadoPor.username").value("gerente"))
                .andExpect(jsonPath("$.comentarioRevision").value("Hay stock en bodega."));
    }

    @Test
    void validaElCambioDeEstado() throws Exception {
        long id = crearSolicitud(usuarioToken, new long[] {tornillo, 1});

        patchJson(URL + "/" + id + "/estado", adminToken, "{\"estado\": \"PENDIENTE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.estado").value("El nuevo estado debe ser APROBADA o RECHAZADA."));
        patchJson(URL + "/" + id + "/estado", adminToken, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.estado").value("El estado es obligatorio."));
        patchJson(URL + "/" + id + "/estado", adminToken, "{\"estado\": \"ENVIADA\"}")
                .andExpect(status().isBadRequest());
        patchJson(URL + "/999999/estado", adminToken, "{\"estado\": \"APROBADA\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void dosRevisionesSimultaneasSoloAplicanUna() throws Exception {
        long id = crearSolicitud(usuarioToken, new long[] {tornillo, 1});
        String gerenteToken = tokenDe("gerente", Rol.GERENTE);
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> resultados = new ArrayList<>();
            for (Map.Entry<String, String> revision : Map.of(adminToken, "APROBADA", gerenteToken, "RECHAZADA").entrySet()) {
                Callable<Integer> tarea = () -> {
                    salida.await();
                    return patchJson(URL + "/" + id + "/estado", revision.getKey(),
                            "{\"estado\": \"" + revision.getValue() + "\", \"comentario\": \"Revisión\"}")
                            .andReturn().getResponse().getStatus();
                };
                resultados.add(executor.submit(tarea));
            }
            salida.countDown();
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> resultado : resultados) {
                estados.add(resultado.get(30, TimeUnit.SECONDS));
            }
            assertThat(estados).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }
    }

    // ------------------------------------------------ GERENTE y catálogos

    @Test
    void gerenteGestionaCatalogosEInvitacionesDeSuEmpresa() throws Exception {
        String gerenteToken = tokenDe("gerente", Rol.GERENTE);
        postJson("/api/v1/proveedores", gerenteToken, """
                {"nit": "800000001", "razonSocial": "Ferretería Central", "email": "ventas@ferre.com"}
                """).andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/invitaciones").header("Authorization", "Bearer " + gerenteToken))
                .andExpect(status().isCreated());
        getJson("/api/auth/me", gerenteToken)
                .andExpect(jsonPath("$.rol").value("GERENTE"))
                .andExpect(jsonPath("$.empresaId").value(empresa.getId()));
    }

    // ---------------------------------------------- integridad referencial

    @Test
    void unProductoEnSolicitudesActivasNoSePuedeDarDeBaja() throws Exception {
        long pendiente = crearSolicitud(usuarioToken, new long[] {tornillo, 1});
        deleteJson("/api/v1/productos/" + tornillo, adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("No se puede eliminar el producto: está incluido en 1 solicitud pendiente o aprobada."));

        patchJson(URL + "/" + pendiente + "/estado", adminToken, "{\"estado\": \"APROBADA\"}").andExpect(status().isOk());
        deleteJson("/api/v1/productos/" + tornillo, adminToken).andExpect(status().isConflict());
    }

    @Test
    void unProductoSoloEnSolicitudesRechazadasSePuedeDarDeBajaYLaSolicitudSigueLegible() throws Exception {
        long rechazada = crearSolicitud(usuarioToken, new long[] {tuerca, 3});
        patchJson(URL + "/" + rechazada + "/estado", adminToken, "{\"estado\": \"RECHAZADA\", \"comentario\": \"No procede\"}")
                .andExpect(status().isOk());

        deleteJson("/api/v1/productos/" + tuerca, adminToken).andExpect(status().isNoContent());

        // El histórico conserva el producto aunque ya no esté en el catálogo.
        getJson(URL + "/" + rechazada, usuarioToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detalles[0].producto.sku").value("TUE-001"))
                .andExpect(jsonPath("$.detalles[0].producto.activo").value(false))
                .andExpect(jsonPath("$.detalles[0].cantidad").value(3));
        getJson(URL, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].detalles[0].producto.nombre").value("Tuerca M8"));
    }

    @Test
    void elListadoNoHaceUnaConsultaPorSolicitud() throws Exception {
        for (int i = 0; i < 12; i++) {
            crearSolicitud(usuarioToken, new long[] {tornillo, i + 1}, new long[] {tuerca, 1});
        }
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();

        mockMvc.perform(get(URL + "?size=12").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(12)))
                .andExpect(jsonPath("$.content[11].detalles", hasSize(2)));

        // Usuario del JWT + página + total + líneas (lote) + productos (lote). Con N+1 serían más de 25.
        assertThat(estadisticas.getPrepareStatementCount()).isLessThanOrEqualTo(6);
    }
}
