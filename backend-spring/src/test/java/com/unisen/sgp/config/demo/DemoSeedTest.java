package com.unisen.sgp.config.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.repository.EmpresaRepository;
import com.unisen.sgp.security.CodigosInvitacion;
import com.unisen.sgp.service.UsuarioService;
import com.unisen.sgp.support.BaseDeDatosDePrueba;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Seed de demostración: crea las cinco empresas con datos completos, una segunda ejecución no
 * duplica nada (tampoco inventario ni ventas), las contraseñas se guardan cifradas, una empresa
 * real con un código de demostración no se toca y nunca se ejecuta junto a un perfil de producción.
 */
@SpringBootTest(properties = "app.demo.ejecutar-al-arrancar=false")
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
class DemoSeedTest {

    private static final List<String> TABLAS = List.of("empresas", "usuarios", "proveedores", "productos", "existencias",
            "movimientos_inventario", "ordenes_compra", "detalles_orden_compra", "recepciones", "documentos_orden_compra",
            "ventas", "detalles_venta", "pagos_venta", "devoluciones_venta", "cambios_precio", "politicas_comerciales");

    @Autowired
    private DemoSeedService seed;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MockMvc mockMvc;

    private long real;

    @BeforeEach
    void baseConUnaEmpresaReal() {
        BaseDeDatosDePrueba.vaciar(jdbc);
        // Una empresa real que casualmente usa el código DEMO03: la seed no debe tocarla.
        Empresa empresa = empresaRepository.save(new Empresa("Papelería Real S.A.S.", "901234567-8", "DEMO03",
                CodigosInvitacion.generar()));
        real = empresa.getId();
        usuarioService.crearUsuario("gerente.real", "gerente@real.com", "Gerente", "Real", "OtraClave#2026",
                Rol.ADMINISTRADOR_COMERCIAL, empresa);
    }

    @Test
    void primeraEjecucionCreaLasEmpresasYLaSegundaNoDuplicaNada() throws Exception {
        Map<String, DemoSeedService.Resultado> primera = seed.sembrar();
        assertThat(primera).containsEntry("DEMO01", DemoSeedService.Resultado.CREADA)
                .containsEntry("DEMO02", DemoSeedService.Resultado.CREADA)
                .containsEntry("DEMO03", DemoSeedService.Resultado.OMITIDA)
                .containsEntry("DEMO04", DemoSeedService.Resultado.CREADA)
                .containsEntry("DEMO05", DemoSeedService.Resultado.CREADA);

        // Datos completos por empresa demo.
        for (String codigo : List.of("DEMO01", "DEMO02", "DEMO04", "DEMO05")) {
            long id = jdbc.queryForObject("SELECT id FROM empresas WHERE codigo = ? AND demo = TRUE", Long.class, codigo);
            assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE empresa_id = ? AND rol = 'ADMINISTRADOR_COMERCIAL'", id))
                    .as(codigo).isEqualTo(1);
            assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE empresa_id = ? AND rol = 'TRABAJADOR'", id))
                    .as(codigo).isGreaterThanOrEqualTo(2);
            assertThat(contar("SELECT COUNT(*) FROM productos WHERE empresa_id = ? AND precio_venta IS NOT NULL "
                    + "AND codigo_barras IS NOT NULL", id)).as(codigo).isGreaterThanOrEqualTo(6);
            assertThat(contar("SELECT COUNT(DISTINCT tratamiento_iva || tarifa_iva) FROM productos WHERE empresa_id = ?", id))
                    .as(codigo + ": tratamientos del IVA variados").isGreaterThanOrEqualTo(2);
            assertThat(contar("SELECT COUNT(*) FROM existencias WHERE empresa_id = ? AND cantidad = 0", id))
                    .as(codigo + ": productos agotados").isGreaterThanOrEqualTo(1);
            assertThat(contar("SELECT COUNT(*) FROM ordenes_compra WHERE empresa_id = ?", id)).as(codigo).isEqualTo(3);
            assertThat(contar("SELECT COUNT(*) FROM ordenes_compra WHERE empresa_id = ? AND estado = 'RECIBIDA'", id)).isEqualTo(1);
            assertThat(contar("SELECT COUNT(*) FROM ordenes_compra WHERE empresa_id = ? AND estado = 'RECIBIDA_PARCIALMENTE'", id))
                    .isEqualTo(1);
            assertThat(contar("SELECT COUNT(*) FROM ventas WHERE empresa_id = ?", id)).as(codigo).isGreaterThanOrEqualTo(4);
            assertThat(contar("SELECT COUNT(*) FROM detalles_venta WHERE empresa_id = ? AND descuento_tipo IS NOT NULL", id))
                    .as(codigo + ": ventas con descuento").isGreaterThanOrEqualTo(1);
            assertThat(contar("SELECT COUNT(*) FROM detalles_venta WHERE empresa_id = ? AND bajo_costo = TRUE", id))
                    .as(codigo + ": venta bajo el costo").isGreaterThanOrEqualTo(1);
            assertThat(contar("SELECT COUNT(*) FROM devoluciones_venta WHERE empresa_id = ?", id)).as(codigo).isEqualTo(1);
            // Cada venta y cada recepción dejó su movimiento por el mecanismo de inventario.
            assertThat(contar("SELECT COUNT(*) FROM movimientos_inventario WHERE empresa_id = ? AND tipo = 'VENTA'", id))
                    .isEqualTo(contar("SELECT COUNT(*) FROM detalles_venta WHERE empresa_id = ?", id));
            assertThat(contar("SELECT COUNT(*) FROM movimientos_inventario WHERE empresa_id = ? AND tipo = 'RECEPCION'", id))
                    .isEqualTo(contar("SELECT COUNT(*) FROM detalles_recepcion WHERE empresa_id = ?", id));
            // El saldo de cada producto es la suma de sus movimientos.
            assertThat(contar("SELECT COUNT(*) FROM existencias e WHERE e.empresa_id = ? AND e.cantidad <> "
                    + "(SELECT COALESCE(SUM(m.cantidad), 0) FROM movimientos_inventario m WHERE m.producto_id = e.producto_id)", id))
                    .isZero();
        }
        // Ninguna cuenta SUPER_ADMIN; contraseñas cifradas con el PasswordEncoder configurado.
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE rol = 'SUPER_ADMIN'")).isZero();
        String hash = jdbc.queryForObject("SELECT password_hash FROM usuarios WHERE username = 'demo1.ana'", String.class);
        assertThat(hash).doesNotContain("abcde123!").startsWith("$2");
        assertThat(passwordEncoder.matches(DemoSeedService.PASSWORD_DEMO, hash)).isTrue();
        // La empresa real quedó intacta.
        assertThat(jdbc.queryForObject("SELECT nombre || '|' || CASE WHEN demo THEN 'demo' ELSE 'real' END "
                + "FROM empresas WHERE id = ?", String.class, real)).isEqualTo("Papelería Real S.A.S.|real");
        assertThat(contar("SELECT COUNT(*) FROM productos WHERE empresa_id = ?", real)).isZero();

        Map<String, Integer> antes = conteos();
        Map<String, Integer> stockAntes = existenciasPorProducto();
        Map<String, DemoSeedService.Resultado> segunda = seed.sembrar();
        assertThat(segunda).containsEntry("DEMO01", DemoSeedService.Resultado.ACTUALIZADA)
                .containsEntry("DEMO03", DemoSeedService.Resultado.OMITIDA);
        assertThat(conteos()).isEqualTo(antes);
        assertThat(existenciasPorProducto()).isEqualTo(stockAntes);

        // Se puede iniciar sesión con las cuentas de demostración.
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo1.admin\",\"password\":\"abcde123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.rol").value("ADMINISTRADOR_COMERCIAL"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"gerente.real\",\"password\":\"abcde123!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unaContrasenaCambiadaNoSeSobrescribe() {
        seed.sembrar();
        jdbc.update("UPDATE usuarios SET password_hash = ? WHERE username = 'demo2.sara'", passwordEncoder.encode("NuevaClave#1"));
        seed.sembrar();
        String hash = jdbc.queryForObject("SELECT password_hash FROM usuarios WHERE username = 'demo2.sara'", String.class);
        assertThat(passwordEncoder.matches("NuevaClave#1", hash)).isTrue();
    }

    @Test
    void nuncaSeEjecutaConUnPerfilDeProduccion() {
        MockEnvironment produccion = new MockEnvironment();
        produccion.setActiveProfiles("demo", "prod");
        assertThatThrownBy(() -> DemoSeedRunner.comprobarEntorno(produccion)).isInstanceOf(IllegalStateException.class);
        MockEnvironment desarrollo = new MockEnvironment();
        desarrollo.setActiveProfiles("demo");
        DemoSeedRunner.comprobarEntorno(desarrollo);
    }

    private int contar(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private Map<String, Integer> conteos() {
        Map<String, Integer> conteos = new LinkedHashMap<>();
        TABLAS.forEach(t -> conteos.put(t, contar("SELECT COUNT(*) FROM " + t)));
        return conteos;
    }

    private Map<String, Integer> existenciasPorProducto() {
        Map<String, Integer> stock = new LinkedHashMap<>();
        jdbc.query("SELECT p.sku, e.cantidad FROM existencias e JOIN productos p ON p.id = e.producto_id ORDER BY p.sku",
                rs -> {
                    stock.put(rs.getString(1), rs.getInt(2));
                });
        return stock;
    }
}
