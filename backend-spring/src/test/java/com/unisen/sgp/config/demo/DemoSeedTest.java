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
import java.lang.reflect.Constructor;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Seed de demostración: crea las empresas con datos completos, una segunda ejecución no duplica
 * nada (tampoco inventario ni ventas), las contraseñas se guardan cifradas, una empresa real con un
 * código de demostración no se toca, nunca se ejecuta junto a un perfil de producción y no siembra
 * sin {@code DEMO_PASSWORD}. Los escenarios de DEMO01 y DEMO02 se prueban en {@link EscenariosDemoTest}.
 *
 * <p>La contraseña demo se genera al azar en cada ejecución de los tests: no hay ninguna fija.
 */
@SpringBootTest(properties = "app.demo.ejecutar-al-arrancar=false")
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
@ExtendWith(OutputCaptureExtension.class)
class DemoSeedTest {

    static final List<String> TABLAS = List.of("empresas", "usuarios", "codigos_invitacion", "proveedores", "productos",
            "existencias", "movimientos_inventario", "solicitudes", "detalles_solicitud", "ordenes_compra",
            "detalles_orden_compra", "programaciones_entrega", "recepciones", "detalles_recepcion", "incidencias_recepcion",
            "documentos_orden_compra", "ventas", "detalles_venta", "pagos_venta", "devoluciones_venta", "cambios_precio",
            "politicas_comerciales");

    private static final String PASSWORD = passwordAleatoria();

    @DynamicPropertySource
    static void passwordDemo(DynamicPropertyRegistry registro) {
        registro.add("app.demo.password", () -> PASSWORD);
    }

    /** 24 caracteres base64url (144 bits) de un generador criptográfico, distintos en cada ejecución. */
    static String passwordAleatoria() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Autowired
    private DemoSeedService seed;
    @Autowired
    private ApplicationContext contexto;
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
    void primeraEjecucionCreaLasEmpresasYLaSegundaNoDuplicaNada(CapturedOutput salida) throws Exception {
        Map<String, DemoSeedService.Resultado> primera = seed.sembrar();
        assertThat(primera).containsEntry("DEMO01", DemoSeedService.Resultado.CREADA)
                .containsEntry("DEMO02", DemoSeedService.Resultado.CREADA)
                .containsEntry("DEMO03", DemoSeedService.Resultado.OMITIDA)
                .containsEntry("DEMO04", DemoSeedService.Resultado.CREADA)
                .containsEntry("DEMO05", DemoSeedService.Resultado.CREADA)
                .containsEntry("DEMO06", DemoSeedService.Resultado.CREADA);

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
            // Las tres órdenes base (DEMO01 y DEMO02 suman las de sus escenarios: ver EscenariosDemoTest).
            for (String observaciones : List.of(DemoSeedService.ORDEN_COMPLETA, DemoSeedService.ORDEN_PARCIAL,
                    DemoSeedService.ORDEN_PENDIENTE)) {
                assertThat(contar("SELECT COUNT(*) FROM ordenes_compra WHERE empresa_id = ? AND observaciones = ?", id,
                        observaciones)).as(codigo + ": " + observaciones).isEqualTo(1);
            }
            assertThat(jdbc.queryForObject("SELECT estado FROM ordenes_compra WHERE empresa_id = ? AND observaciones = ?",
                    String.class, id, DemoSeedService.ORDEN_COMPLETA)).isEqualTo("RECIBIDA");
            assertThat(jdbc.queryForObject("SELECT estado FROM ordenes_compra WHERE empresa_id = ? AND observaciones = ?",
                    String.class, id, DemoSeedService.ORDEN_PARCIAL)).isEqualTo("RECIBIDA_PARCIALMENTE");
            assertThat(jdbc.queryForObject("SELECT estado FROM ordenes_compra WHERE empresa_id = ? AND observaciones = ?",
                    String.class, id, DemoSeedService.ORDEN_PENDIENTE)).isEqualTo("EMITIDA");
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
        for (String codigo : List.of("DEMO04", "DEMO05")) {
            long id = jdbc.queryForObject("SELECT id FROM empresas WHERE codigo = ?", Long.class, codigo);
            assertThat(contar("SELECT COUNT(*) FROM ordenes_compra WHERE empresa_id = ?", id)).as(codigo).isEqualTo(3);
            assertThat(contar("SELECT COUNT(*) FROM solicitudes WHERE empresa_id = ?", id)).as(codigo).isZero();
            assertThat(contar("SELECT COUNT(*) FROM codigos_invitacion WHERE empresa_id = ?", id)).as(codigo).isZero();
        }
        // DEMO06: desactivada, con sus dos cuentas y sin datos de negocio.
        long demo06 = jdbc.queryForObject("SELECT id FROM empresas WHERE codigo = 'DEMO06' AND demo = TRUE AND activa = FALSE",
                Long.class);
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE empresa_id = ? AND estado = 'ACTIVO'", demo06)).isEqualTo(2);
        assertThat(contar("SELECT COUNT(*) FROM productos WHERE empresa_id = ?", demo06)).isZero();
        assertThat(contar("SELECT COUNT(*) FROM proveedores WHERE empresa_id = ?", demo06)).isZero();

        // Ninguna cuenta SUPER_ADMIN; contraseñas cifradas con el PasswordEncoder configurado.
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE rol = 'SUPER_ADMIN'")).isZero();
        String hash = jdbc.queryForObject("SELECT password_hash FROM usuarios WHERE username = 'demo1.ana'", String.class);
        assertThat(hash).doesNotContain(PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
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
                        .content(login("demo1.admin", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.rol").value("ADMINISTRADOR_COMERCIAL"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login("gerente.real", PASSWORD)))
                .andExpect(status().isUnauthorized());
        // La contraseña nunca aparece en los logs (dos siembras completas y los inicios de sesión).
        assertThat(salida.getAll()).doesNotContain(PASSWORD);
    }

    @Test
    void unaContrasenaCambiadaNoSeSobrescribe() {
        seed.sembrar();
        jdbc.update("UPDATE usuarios SET password_hash = ? WHERE username = 'demo2.sara'", passwordEncoder.encode("NuevaClave#1"));
        seed.sembrar();
        String hash = jdbc.queryForObject("SELECT password_hash FROM usuarios WHERE username = 'demo2.sara'", String.class);
        assertThat(passwordEncoder.matches("NuevaClave#1", hash)).isTrue();
    }

    /**
     * Ausente (sin {@code DEMO_PASSWORD} la propiedad queda vacía), en blanco, corta o demasiado
     * larga: {@code sembrar()} y el arranque con el perfil demo fallan sin escribir ninguna fila, y
     * el error no repite la contraseña recibida.
     */
    @Test
    void sinUnaContrasenaValidaLaSeedNoEscribeNada() throws Exception {
        Map<String, Integer> antes = conteos();
        MockEnvironment demo = new MockEnvironment();
        demo.setActiveProfiles("demo");
        for (String invalida : new String[] {null, "", "   ", "corta12", "ñ".repeat(UsuarioService.PASSWORD_MAX_BYTES / 2 + 1)}) {
            DemoSeedService conInvalida = seedConPassword(invalida);
            assertThatThrownBy(conInvalida::sembrar)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DEMO_PASSWORD")
                    .satisfies(e -> {
                        if (invalida != null && !invalida.isBlank()) {
                            assertThat(e.getMessage()).doesNotContain(invalida);
                        }
                    });
            assertThatThrownBy(() -> new DemoSeedRunner(conInvalida, demo).run(null))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(conteos()).as("sin escrituras con %s", invalida == null ? "null" : "una contraseña inválida")
                    .isEqualTo(antes);
        }
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

    /** Una instancia de la seed con los beans del contexto y otra contraseña (el resto, idéntico). */
    private DemoSeedService seedConPassword(String password) throws Exception {
        Constructor<?> constructor = DemoSeedService.class.getConstructors()[0];
        Object[] argumentos = Arrays.stream(constructor.getParameterTypes())
                .map(tipo -> tipo == String.class ? password : contexto.getBean(tipo))
                .toArray();
        return (DemoSeedService) constructor.newInstance(argumentos);
    }

    static String login(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
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
