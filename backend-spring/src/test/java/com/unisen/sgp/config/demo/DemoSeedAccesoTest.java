package com.unisen.sgp.config.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisen.sgp.support.BaseDeDatosDePrueba;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Las diez cuentas demo por HTTP, como las usará quien pruebe la aplicación: quién entra y quién
 * no (y por qué), qué ve cada rol y el aislamiento entre Mercado Verde y Suministros del Caribe
 * (que comparten NIT de proveedor, SKU y código de barras).
 *
 * <p>El estado INACTIVO no tiene cuenta demo (la seed no añade usuarios para representarlo): lo
 * cubren {@code AuthControllerTest} y {@code OnboardingTrabajadoresTest}.
 */
@SpringBootTest(properties = "app.demo.ejecutar-al-arrancar=false")
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
class DemoSeedAccesoTest {

    private static final String PASSWORD = DemoSeedTest.passwordAleatoria();

    @DynamicPropertySource
    static void passwordDemo(DynamicPropertyRegistry registro) {
        registro.add("app.demo.password", () -> PASSWORD);
    }

    @Autowired
    private DemoSeedService seed;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void sembrar() {
        BaseDeDatosDePrueba.vaciar(jdbc);
        seed.sembrar();
    }

    @Test
    void entranLasCuentasActivasYLasDemasConocenElMotivo() throws Exception {
        for (String admin : new String[] {"carlos.mendoza", "andres.rojas", "diego.martinez", "miguel.ortiz"}) {
            login(admin, PASSWORD).andExpect(status().isOk())
                    .andExpect(jsonPath("$.usuario.rol").value("ADMINISTRADOR_COMERCIAL"));
        }
        for (String trabajador : new String[] {"laura.torres", "maria.castro"}) {
            login(trabajador, PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.usuario.rol").value("TRABAJADOR"));
        }
        login("carlos.mendoza", PASSWORD).andExpect(jsonPath("$.usuario.empresaNombre").value("Mercado Verde S.A.S."));
        rechazado("sofia.ramirez", "PENDIENTE");
        rechazado("valentina.gomez", "RECHAZADO");
        rechazado("sergio.vega", "EMPRESA_INACTIVA");
        rechazado("paula.navarro", "EMPRESA_INACTIVA");
        login("carlos.mendoza", PASSWORD + "x").andExpect(status().isUnauthorized());
    }

    @Test
    void cadaRolVeLoSuyoYNadaDeLaOtraEmpresa() throws Exception {
        String carlos = token("carlos.mendoza");
        String andres = token("andres.rojas");
        String laura = token("laura.torres");
        String maria = token("maria.castro");
        long solicitudSc = id("SELECT MIN(s.id) FROM solicitudes s JOIN empresas e ON e.id = s.empresa_id WHERE e.codigo = 'SCA002'");
        long ordenSc = id("SELECT MIN(o.id) FROM ordenes_compra o JOIN empresas e ON e.id = o.empresa_id WHERE e.codigo = 'SCA002'");
        long ordenMv = id("SELECT MIN(o.id) FROM ordenes_compra o JOIN empresas e ON e.id = o.empresa_id WHERE e.codigo = 'MVSA01'");
        long productoSc = id("SELECT MIN(p.id) FROM productos p JOIN empresas e ON e.id = p.empresa_id WHERE e.codigo = 'SCA002'");

        // El administrador: solo su empresa; nunca «Empresas» (SUPER_ADMIN).
        total(carlos, "/api/v1/solicitudes?size=50", 3);
        total(carlos, "/api/v1/ordenes-compra?size=50", 3);
        total(carlos, "/api/v1/productos?size=50", 7);
        total(carlos, "/api/v1/trabajadores?size=50", 1);
        total(carlos, "/api/auth/invitaciones?size=50", 0);
        total(andres, "/api/v1/solicitudes?size=50", 1);
        total(andres, "/api/v1/ordenes-compra?size=50", 2);
        total(andres, "/api/v1/productos?size=50", 4);
        get(carlos, "/api/v1/solicitudes/" + solicitudSc).andExpect(status().isNotFound());
        get(carlos, "/api/v1/ordenes-compra/" + ordenSc).andExpect(status().isNotFound());
        get(carlos, "/api/v1/productos/" + productoSc).andExpect(status().isNotFound());
        get(andres, "/api/v1/ordenes-compra/" + ordenMv).andExpect(status().isNotFound());
        get(carlos, "/api/v1/empresas").andExpect(status().isForbidden());

        // El trabajador: sus solicitudes; sin gestión de invitaciones ni trabajadores.
        total(laura, "/api/v1/solicitudes?size=50", 3);
        total(maria, "/api/v1/solicitudes?size=50", 1);
        get(laura, "/api/v1/solicitudes/" + solicitudSc).andExpect(status().isNotFound());
        get(laura, "/api/auth/invitaciones").andExpect(status().isForbidden());
        get(laura, "/api/v1/trabajadores").andExpect(status().isForbidden());
        get(laura, "/api/v1/empresas").andExpect(status().isForbidden());

        // Mismo código de barras en las dos empresas: cada una encuentra el suyo.
        long deMv = productoPorCodigo(carlos);
        long deSc = productoPorCodigo(andres);
        assertThat(deMv).isNotEqualTo(deSc);
        assertThat(id("SELECT e.id FROM productos p JOIN empresas e ON e.id = p.empresa_id WHERE p.id = ?", deMv))
                .isEqualTo(id("SELECT id FROM empresas WHERE codigo = 'MVSA01'"));
        assertThat(id("SELECT e.id FROM productos p JOIN empresas e ON e.id = p.empresa_id WHERE p.id = ?", deSc))
                .isEqualTo(id("SELECT id FROM empresas WHERE codigo = 'SCA002'"));
    }

    /** El flujo que se enseña en la demostración: el administrador aprueba al trabajador pendiente. */
    @Test
    void elAdministradorDeNovaApruebaASuTrabajadorPendiente() throws Exception {
        String diego = token("diego.martinez");
        long sofia = id("SELECT id FROM usuarios WHERE username = 'sofia.ramirez'");
        total(diego, "/api/v1/trabajadores?estado=PENDIENTE", 1);
        total(token("miguel.ortiz"), "/api/v1/trabajadores?estado=RECHAZADO", 1);
        // Otro administrador no puede tocarla.
        mockMvc.perform(patch("/api/v1/trabajadores/" + sofia + "/estado").header("Authorization", "Bearer " + token("carlos.mendoza"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"ACTIVO\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/trabajadores/" + sofia + "/estado").header("Authorization", "Bearer " + diego)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"ACTIVO\"}"))
                .andExpect(status().isOk());
        login("sofia.ramirez", PASSWORD).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- utilidades

    private long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))));
    }

    private String token(String username) throws Exception {
        String json = login(username, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("accessToken").asText();
    }

    private void rechazado(String username, String motivo) throws Exception {
        login(username, PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.motivo").value(motivo))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    private ResultActions get(String token, String url) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(url)
                .header("Authorization", "Bearer " + token));
    }

    private void total(String token, String url, int esperado) throws Exception {
        get(token, url).andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(esperado));
    }

    private long productoPorCodigo(String token) throws Exception {
        String json = get(token, "/api/v1/productos/buscar?codigo=" + DatosDemo.CODIGO_BARRAS_COMPARTIDO)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("producto").get("id").asLong();
    }
}
