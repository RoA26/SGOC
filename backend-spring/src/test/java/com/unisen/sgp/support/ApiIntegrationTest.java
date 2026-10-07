package com.unisen.sgp.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.service.UsuarioService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base de los tests HTTP de catálogos: BD limpia, un ADMIN y un USUARIO con tokens reales
 * obtenidos vía /api/auth/login (se ejercita la cadena de seguridad completa).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiIntegrationTest {

    private static final String PASSWORD = "S3gura!Password";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    private UsuarioService usuarioService;

    protected String adminToken;
    protected String usuarioToken;

    @BeforeEach
    void prepararBaseDeDatos() throws Exception {
        // SQL directo: repository.deleteAll() haría borrado lógico y dejaría las filas.
        jdbcTemplate.update("DELETE FROM productos");
        jdbcTemplate.update("DELETE FROM proveedores");
        jdbcTemplate.update("DELETE FROM codigos_invitacion");
        jdbcTemplate.update("DELETE FROM usuarios");

        usuarioService.crearUsuario("admin", "admin@unisen.com", "Admin", PASSWORD, Rol.ADMIN);
        usuarioService.crearUsuario("compras", "compras@unisen.com", "Compras", PASSWORD, Rol.USUARIO);
        adminToken = login("admin");
        usuarioToken = login("compras");
    }

    protected String login(String username) throws Exception {
        return login(username, PASSWORD);
    }

    protected String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected JsonNode leer(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected ResultActions getJson(String url, String token) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    protected ResultActions postJson(String url, String token, String body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions putJson(String url, String token, String body) throws Exception {
        return mockMvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions deleteJson(String url, String token) throws Exception {
        return mockMvc.perform(delete(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    /** Crea un proveedor como ADMIN y devuelve su id. */
    protected long crearProveedor(String nit, String razonSocial) throws Exception {
        String body = """
                {"nit": "%s", "razonSocial": "%s", "email": "contacto@%s.com"}
                """.formatted(nit, razonSocial, nit.replaceAll("\\D", ""));
        return leer(postJson("/api/v1/proveedores", adminToken, body)).get("id").asLong();
    }

    /** Crea un producto como ADMIN y devuelve su id. */
    protected long crearProducto(String sku, String nombre, String precio, long proveedorId) throws Exception {
        String body = """
                {"sku": "%s", "nombre": "%s", "precio": %s, "proveedorId": %d}
                """.formatted(sku, nombre, precio, proveedorId);
        return leer(postJson("/api/v1/productos", adminToken, body)).get("id").asLong();
    }
}
