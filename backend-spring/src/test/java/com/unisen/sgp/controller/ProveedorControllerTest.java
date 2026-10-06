package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.unisen.sgp.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class ProveedorControllerTest extends ApiIntegrationTest {

    private static final String URL = "/api/v1/proveedores";

    @Test
    void creaProveedorNormalizandoLosDatos() throws Exception {
        String body = """
                {"nit": " 900.123.456-7 ", "razonSocial": "  Suministros SAS ", "email": "Compras@Prov.COM",
                 "telefono": "  ", "direccion": ""}
                """;
        var resultado = postJson(URL, adminToken, body);
        JsonNode creado = leer(resultado);
        resultado
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith(URL + "/" + creado.get("id").asLong())))
                .andExpect(jsonPath("$.nit").value("900123456-7"))
                .andExpect(jsonPath("$.razonSocial").value("Suministros SAS"))
                .andExpect(jsonPath("$.email").value("compras@prov.com"))
                .andExpect(jsonPath("$.telefono").doesNotExist())
                .andExpect(jsonPath("$.direccion").doesNotExist())
                .andExpect(jsonPath("$.creadoEn").exists());
        assertThat(creado.get("actualizadoEn").asText())
                .as("en el alta, creadoEn y actualizadoEn coinciden")
                .isEqualTo(creado.get("creadoEn").asText());

        getJson(URL + "/" + creado.get("id").asLong(), usuarioToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nit").value("900123456-7"));
    }

    @Test
    void listaPaginadaYOrdenadaPorRazonSocial() throws Exception {
        crearProveedor("800000003", "Cementos del Caribe");
        crearProveedor("800000001", "Aceros Andinos");
        crearProveedor("800000002", "Bombas y Válvulas");

        getJson(URL + "?size=2", usuarioToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].razonSocial").value("Aceros Andinos"))
                .andExpect(jsonPath("$.content[1].razonSocial").value("Bombas y Válvulas"))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));

        getJson(URL + "?size=2&page=1", usuarioToken)
                .andExpect(jsonPath("$.content[0].razonSocial").value("Cementos del Caribe"));

        getJson(URL + "?sort=razonSocial,desc", usuarioToken)
                .andExpect(jsonPath("$.content[0].razonSocial").value("Cementos del Caribe"));
    }

    @Test
    void elTamanoDePaginaEstaLimitado() throws Exception {
        getJson(URL + "?size=5000", usuarioToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(100));
    }

    @Test
    void validaLosCamposConMensajesPorCampo() throws Exception {
        String body = """
                {"nit": "12", "razonSocial": "", "email": "no-es-correo", "telefono": "abc"}
                """;
        postJson(URL, adminToken, body)
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.nit").exists())
                .andExpect(jsonPath("$.errors.razonSocial").value("La razón social es obligatoria."))
                .andExpect(jsonPath("$.errors.email").value("El correo no tiene un formato válido."))
                .andExpect(jsonPath("$.errors.telefono").exists());
    }

    @Test
    void nitDuplicadoDevuelve409ConElCampoAfectado() throws Exception {
        crearProveedor("900123456-7", "Original");

        String body = """
                {"nit": "900.123.456-7", "razonSocial": "Copia", "email": "copia@prov.com"}
                """;
        postJson(URL, adminToken, body)
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.errors.nit").value(org.hamcrest.Matchers.startsWith("Ya existe un proveedor con este NIT")));
    }

    @Test
    void actualizaProveedorYDetectaNitDeOtro() throws Exception {
        long id = crearProveedor("800000001", "Aceros Andinos");
        crearProveedor("800000002", "Bombas y Válvulas");

        putJson(URL + "/" + id, adminToken, """
                {"nit": "800000001", "razonSocial": "Aceros Andinos S.A.", "email": "ventas@aceros.com",
                 "telefono": "+57 601 555 1234", "direccion": "Calle 1 # 2-3"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razonSocial").value("Aceros Andinos S.A."))
                .andExpect(jsonPath("$.telefono").value("+57 601 555 1234"));

        putJson(URL + "/" + id, adminToken, """
                {"nit": "800000002", "razonSocial": "Aceros Andinos S.A.", "email": "ventas@aceros.com"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.nit").exists());
    }

    @Test
    void actualizarOObtenerInexistenteDevuelve404() throws Exception {
        getJson(URL + "/999999", usuarioToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe el proveedor con id 999999."));
        putJson(URL + "/999999", adminToken, """
                {"nit": "800000001", "razonSocial": "X", "email": "x@x.com"}
                """).andExpect(status().isNotFound());
    }

    @Test
    void eliminarEsBorradoLogico() throws Exception {
        long id = crearProveedor("800000001", "Aceros Andinos");

        deleteJson(URL + "/" + id, adminToken).andExpect(status().isNoContent());

        getJson(URL + "/" + id, adminToken).andExpect(status().isNotFound());
        getJson(URL, adminToken).andExpect(jsonPath("$.page.totalElements").value(0));
        deleteJson(URL + "/" + id, adminToken).andExpect(status().isNotFound());

        Boolean activo = jdbcTemplate.queryForObject("SELECT activo FROM proveedores WHERE id = ?", Boolean.class, id);
        assertThat(activo).as("la fila sigue en la BD, marcada como inactiva").isFalse();
    }

    @Test
    void elNitDeUnProveedorDadoDeBajaNoSeReutiliza() throws Exception {
        long id = crearProveedor("800000001", "Aceros Andinos");
        deleteJson(URL + "/" + id, adminToken).andExpect(status().isNoContent());

        postJson(URL, adminToken, """
                {"nit": "800000001", "razonSocial": "Otro", "email": "otro@prov.com"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.nit").exists());
    }

    @Test
    void unUsuarioSinRolAdminSoloPuedeLeer() throws Exception {
        long id = crearProveedor("800000001", "Aceros Andinos");
        String body = """
                {"nit": "800000009", "razonSocial": "Nuevo", "email": "nuevo@prov.com"}
                """;

        getJson(URL, usuarioToken).andExpect(status().isOk());
        postJson(URL, usuarioToken, body)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No tienes permisos para acceder a este recurso."));
        putJson(URL + "/" + id, usuarioToken, body).andExpect(status().isForbidden());
        deleteJson(URL + "/" + id, usuarioToken).andExpect(status().isForbidden());
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void parametrosInvalidosDevuelven400() throws Exception {
        getJson(URL + "?sort=campoInexistente", usuarioToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("campoInexistente")));
        getJson(URL + "/abc", usuarioToken).andExpect(status().isBadRequest());
    }
}
