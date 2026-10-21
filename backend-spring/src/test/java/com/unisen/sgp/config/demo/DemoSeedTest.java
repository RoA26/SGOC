package com.unisen.sgp.config.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.unisen.sgp.model.dto.CambioEstadoEmpresaDTO;
import com.unisen.sgp.model.dto.CambioEstadoUsuarioDTO;
import com.unisen.sgp.model.dto.ProductoRequestDTO;
import com.unisen.sgp.model.dto.ProveedorRequestDTO;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.EstadoUsuario;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.EmpresaRepository;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.security.CodigosInvitacion;
import com.unisen.sgp.service.EmpresaService;
import com.unisen.sgp.service.ProductoService;
import com.unisen.sgp.service.ProveedorService;
import com.unisen.sgp.service.UsuarioService;
import com.unisen.sgp.service.comercial.DevolucionVentaService;
import com.unisen.sgp.support.BaseDeDatosDePrueba;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seed de demostración: crea exactamente las cinco empresas y las diez cuentas previstas con sus
 * datos, una segunda ejecución no duplica ni cambia nada, se detiene sin escribir ante cualquier
 * colisión con datos ajenos, no reactiva ni cambia cuentas existentes, se reanuda tras un fallo
 * parcial, nunca se ejecuta junto a un perfil de producción y no siembra sin {@code DEMO_PASSWORD}.
 * El acceso y el aislamiento por HTTP se prueban en {@link DemoSeedAccesoTest}.
 *
 * <p>La contraseña demo se genera al azar en cada ejecución de los tests: no hay ninguna fija.
 */
@SpringBootTest(properties = "app.demo.ejecutar-al-arrancar=false")
@ActiveProfiles({"test", "demo"})
@ExtendWith(OutputCaptureExtension.class)
class DemoSeedTest {

    static final List<String> CODIGOS = List.of("MVSA01", "SCA002", "DNS003", "CHS004", "LAS005");

    /** Todas las tablas de la aplicación (las de negocio llevan {@code empresa_id}). */
    static final List<String> TABLAS = List.of("empresas", "usuarios", "codigos_invitacion", "proveedores", "productos",
            "existencias", "movimientos_inventario", "solicitudes", "detalles_solicitud", "ordenes_compra",
            "detalles_orden_compra", "programaciones_entrega", "recepciones", "detalles_recepcion", "incidencias_recepcion",
            "documentos_orden_compra", "ventas", "detalles_venta", "pagos_venta", "devoluciones_venta",
            "detalles_devolucion_venta", "autorizaciones_comerciales", "cambios_precio", "politicas_comerciales");

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
    @MockitoSpyBean
    private DevolucionVentaService devolucionService;
    @Autowired
    private ApplicationContext contexto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private EmpresaService empresaService;
    @Autowired
    private ProveedorService proveedorService;
    @Autowired
    private ProductoService productoService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TransactionTemplate transaccion;

    private long real;

    /** Una empresa real con el mismo NIT de proveedor, SKU y código de barras que las empresas demo. */
    @BeforeEach
    void baseConUnaEmpresaReal() {
        BaseDeDatosDePrueba.vaciar(jdbc);
        real = empresaReal();
    }

    private long empresaReal() {
        Empresa empresa = empresaRepository.save(new Empresa("Suministros Reales S.A.S.", "901555444-1", "REAL01",
                CodigosInvitacion.generar()));
        Usuario gerente = usuarioService.crearUsuario("gerente.real", "gerente@real.example", "Gerente", "Real",
                "OtraClave#2026", Rol.ADMINISTRADOR_COMERCIAL, empresa);
        DemoSeedService.como(gerente, empresa, () -> {
            long proveedor = proveedorService.crear(new ProveedorRequestDTO(DatosDemo.NIT_COMPARTIDO, "Proveedor Real Ltda.",
                    "compras@proveedor-real.example", null, null)).id();
            return productoService.crear(new ProductoRequestDTO(DatosDemo.SKU_COMPARTIDO, DatosDemo.CODIGO_BARRAS_COMPARTIDO,
                    "Producto real", null, new BigDecimal("1000"), 1, proveedor)).id();
        });
        return empresa.getId();
    }

    @Test
    void creaLasCincoEmpresasYLasDiezCuentasYUnaSegundaEjecucionNoCambiaNada(CapturedOutput salida) {
        String realAntes = huellaDe(real);

        assertThat(seed.sembrar()).containsExactlyEntriesOf(todas(DemoSeedService.Resultado.CREADA));

        // Exactamente cinco empresas demo y diez cuentas, una de cada rol por empresa.
        assertThat(jdbc.queryForList("SELECT codigo || '|' || nombre || '|' || CASE WHEN activa THEN 'activa' ELSE 'inactiva' END "
                + "FROM empresas WHERE demo ORDER BY codigo",
                String.class)).containsExactly("CHS004|Comercializadora Horizonte S.A.S.|activa",
                "DNS003|Distribuciones Nova S.A.S.|activa", "LAS005|Logística Andina S.A.S.|inactiva",
                "MVSA01|Mercado Verde S.A.S.|activa", "SCA002|Suministros del Caribe S.A.S.|activa");
        assertThat(jdbc.queryForList("SELECT e.codigo || '|' || u.rol || '|' || u.username || '|' || u.estado FROM usuarios u "
                + "JOIN empresas e ON e.id = u.empresa_id WHERE e.demo ORDER BY e.codigo, u.rol", String.class))
                .containsExactly(
                        "CHS004|ADMINISTRADOR_COMERCIAL|miguel.ortiz|ACTIVO", "CHS004|TRABAJADOR|valentina.gomez|RECHAZADO",
                        "DNS003|ADMINISTRADOR_COMERCIAL|diego.martinez|ACTIVO", "DNS003|TRABAJADOR|sofia.ramirez|PENDIENTE",
                        "LAS005|ADMINISTRADOR_COMERCIAL|sergio.vega|ACTIVO", "LAS005|TRABAJADOR|paula.navarro|ACTIVO",
                        "MVSA01|ADMINISTRADOR_COMERCIAL|carlos.mendoza|ACTIVO", "MVSA01|TRABAJADOR|laura.torres|ACTIVO",
                        "SCA002|ADMINISTRADOR_COMERCIAL|andres.rojas|ACTIVO", "SCA002|TRABAJADOR|maria.castro|ACTIVO");
        assertThat(contar("SELECT COUNT(*) FROM usuarios u JOIN empresas e ON e.id = u.empresa_id WHERE e.demo "
                + "AND u.email LIKE '%@' || LOWER(e.codigo) || '.sgp-demo.test'")).isEqualTo(10);
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE rol = 'SUPER_ADMIN'")).isZero();
        // Contraseñas cifradas con el PasswordEncoder configurado.
        String hash = jdbc.queryForObject("SELECT password_hash FROM usuarios WHERE username = 'laura.torres'", String.class);
        assertThat(hash).doesNotContain(PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();

        // Mercado Verde: flujo comercial completo.
        long mv = empresa("MVSA01");
        assertThat(contar("SELECT COUNT(*) FROM proveedores WHERE empresa_id = ?", mv)).isEqualTo(3);
        assertThat(contar("SELECT COUNT(*) FROM productos WHERE empresa_id = ? AND precio_venta IS NOT NULL "
                + "AND codigo_barras IS NOT NULL", mv)).isEqualTo(7);
        assertThat(contar("SELECT COUNT(DISTINCT tratamiento_iva || tarifa_iva) FROM productos WHERE empresa_id = ?", mv))
                .as("tratamientos del IVA variados").isGreaterThanOrEqualTo(3);
        assertThat(contar("SELECT COUNT(*) FROM politicas_comerciales WHERE empresa_id = ?", mv)).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM existencias WHERE empresa_id = ? AND cantidad = 0", mv)).as("agotado").isEqualTo(1);
        assertThat(porEstado("solicitudes", mv)).containsExactlyInAnyOrderEntriesOf(
                Map.of("PENDIENTE", 1, "RECHAZADA", 1, "APROBADA", 1));
        assertThat(contar("SELECT COUNT(*) FROM solicitudes WHERE empresa_id = ? AND estado = 'RECHAZADA' "
                + "AND comentario_revision IS NOT NULL AND revisado_por_id IS NOT NULL", mv)).isEqualTo(1);
        assertThat(porEstado("ordenes_compra", mv)).containsExactlyInAnyOrderEntriesOf(
                Map.of("RECIBIDA", 1, "RECIBIDA_PARCIALMENTE", 1, "EMITIDA", 1));
        assertThat(jdbc.queryForObject("SELECT estado FROM ordenes_compra WHERE empresa_id = ? AND solicitud_id IS NOT NULL",
                String.class, mv)).isEqualTo("RECIBIDA_PARCIALMENTE");
        assertThat(contar("SELECT COUNT(*) FROM recepciones WHERE empresa_id = ?", mv)).isEqualTo(2);
        assertThat(contar("SELECT COUNT(*) FROM incidencias_recepcion WHERE empresa_id = ? AND tipo = 'FALTANTE'", mv))
                .isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM ventas WHERE empresa_id = ?", mv)).isEqualTo(5);
        assertThat(contar("SELECT COUNT(*) FROM detalles_venta WHERE empresa_id = ? AND descuento_tipo IS NOT NULL", mv))
                .as("ventas con descuento").isGreaterThanOrEqualTo(2);
        assertThat(contar("SELECT COUNT(*) FROM detalles_venta WHERE empresa_id = ? AND bajo_costo = TRUE", mv))
                .as("venta bajo el costo").isGreaterThanOrEqualTo(1);
        assertThat(contar("SELECT COUNT(DISTINCT medio) FROM pagos_venta WHERE empresa_id = ?", mv)).as("medios de pago")
                .isGreaterThanOrEqualTo(4);
        assertThat(contar("SELECT COUNT(*) FROM devoluciones_venta WHERE empresa_id = ?", mv)).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM movimientos_inventario WHERE empresa_id = ? AND tipo = 'DEVOLUCION_VENTA'", mv))
                .as("la devolución reingresa la unidad").isEqualTo(1);

        // Suministros del Caribe: datos propios, más pocos.
        long sc = empresa("SCA002");
        assertThat(contar("SELECT COUNT(*) FROM productos WHERE empresa_id = ?", sc)).isEqualTo(4);
        assertThat(porEstado("solicitudes", sc)).containsExactlyEntriesOf(Map.of("PENDIENTE", 1));
        assertThat(porEstado("ordenes_compra", sc)).containsExactlyInAnyOrderEntriesOf(Map.of("RECIBIDA", 1, "EMITIDA", 1));
        assertThat(contar("SELECT COUNT(*) FROM ventas WHERE empresa_id = ?", sc)).isEqualTo(2);
        assertThat(contar("SELECT COUNT(*) FROM devoluciones_venta WHERE empresa_id = ?", sc)).isZero();
        // Mismo SKU y código de barras en MV, SC y la empresa real: un producto distinto en cada una.
        assertThat(contar("SELECT COUNT(DISTINCT empresa_id) FROM productos WHERE sku = ? AND codigo_barras = ?",
                DatosDemo.SKU_COMPARTIDO, DatosDemo.CODIGO_BARRAS_COMPARTIDO)).isEqualTo(3);

        // Nova, Horizonte y Andina: solo cuentas.
        for (String codigo : List.of("DNS003", "CHS004", "LAS005")) {
            assertThat(conteosDe(empresa(codigo))).as(codigo).allSatisfy((tabla, n) ->
                    assertThat(n).as(tabla).isEqualTo(tabla.equals("usuarios") ? 2 : 0));
        }

        comprobarCoherencia();
        assertThat(huellaDe(real)).as("la empresa real no cambia").isEqualTo(realAntes);

        Map<String, Integer> conteos = conteos();
        Map<String, Integer> stock = existencias();
        Map<String, String> estados = estados();
        assertThat(seed.sembrar()).containsExactlyEntriesOf(todas(DemoSeedService.Resultado.ACTUALIZADA));
        assertThat(conteos()).isEqualTo(conteos);
        assertThat(existencias()).isEqualTo(stock);
        assertThat(estados()).isEqualTo(estados);
        assertThat(huellaDe(real)).isEqualTo(realAntes);
        // La contraseña nunca aparece en los logs (dos siembras completas).
        assertThat(salida.getAll()).doesNotContain(PASSWORD);
    }

    /** Lo que alguien cambió después en una cuenta demo (contraseña o estado) se respeta. */
    @Test
    void noCambiaContrasenasNiEstadosDeCuentasExistentes() {
        seed.sembrar();
        jdbc.update("UPDATE usuarios SET password_hash = ? WHERE username = 'laura.torres'", passwordEncoder.encode("NuevaClave#1"));
        long sofia = id("SELECT id FROM usuarios WHERE username = 'sofia.ramirez'");
        long valentina = id("SELECT id FROM usuarios WHERE username = 'valentina.gomez'");
        comoEn("DNS003", "diego.martinez", () -> usuarioService.cambiarEstadoTrabajador(sofia,
                new CambioEstadoUsuarioDTO(EstadoUsuario.ACTIVO)));
        comoEn("CHS004", "miguel.ortiz", () -> usuarioService.cambiarEstadoTrabajador(valentina,
                new CambioEstadoUsuarioDTO(EstadoUsuario.ACTIVO)));
        Map<String, Integer> conteos = conteos();

        seed.sembrar();

        assertThat(passwordEncoder.matches("NuevaClave#1",
                jdbc.queryForObject("SELECT password_hash FROM usuarios WHERE username = 'laura.torres'", String.class))).isTrue();
        assertThat(estadoDe("sofia.ramirez")).isEqualTo("ACTIVO");
        assertThat(estadoDe("valentina.gomez")).isEqualTo("ACTIVO");
        assertThat(conteos()).isEqualTo(conteos);
    }

    /**
     * Un código, NIT, username o correo de la seed ocupado por un registro ajeno detiene la seed
     * antes de escribir nada, y ese registro no cambia.
     */
    @Test
    void cualquierColisionDetieneLaSeedSinEscribirNada() {
        Map<String, Consumer<Empresa>> casos = new LinkedHashMap<>();
        casos.put("el código MVSA01", empresa -> empresaRepository.save(new Empresa("Otra Real S.A.S.", "901000111-2",
                "MVSA01", CodigosInvitacion.generar())));
        casos.put("el NIT de SCA002", empresa -> empresaRepository.save(new Empresa("Otra Real S.A.S.", "999100000002",
                "OTRA01", CodigosInvitacion.generar())));
        casos.put("el username laura.torres", empresa -> usuarioService.crearUsuario("laura.torres", "laura@real.example",
                "Laura", "Real", "OtraClave#2026", Rol.TRABAJADOR, empresa));
        casos.put("el correo de andres.rojas", empresa -> usuarioService.crearUsuario("andres.real",
                "andres.rojas@sca002.sgp-demo.test", "Andrés", "Real", "OtraClave#2026", Rol.TRABAJADOR, empresa));
        // Una empresa demo con el código de la seed pero otro NIT no se considera suya.
        casos.put("el código DNS003", empresa -> {
            Empresa demo = new Empresa("Demo antigua", "901000222-3", "DNS003", CodigosInvitacion.generar());
            demo.marcarDemo();
            empresaRepository.save(demo);
        });

        casos.forEach((caso, preparar) -> {
            BaseDeDatosDePrueba.vaciar(jdbc);
            real = empresaReal();
            preparar.accept(empresaRepository.findById(real).orElseThrow());
            Map<String, Integer> antes = conteos();
            String usuariosAntes = jdbc.queryForList("SELECT username || '|' || email || '|' || estado || '|' || password_hash "
                    + "FROM usuarios ORDER BY id", String.class).toString();

            assertThatThrownBy(seed::sembrar).as(caso)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Seed demo detenida sin escribir nada")
                    .hasMessageContaining(caso)
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain(PASSWORD));
            assertThat(conteos()).as(caso).isEqualTo(antes);
            assertThat(jdbc.queryForList("SELECT username || '|' || email || '|' || estado || '|' || password_hash "
                    + "FROM usuarios ORDER BY id", String.class).toString()).as(caso).isEqualTo(usuariosAntes);
        });
    }

    /** Ni en nombre de cuentas sin acceso ni en una empresa desactivada; nada se reactiva. */
    @Test
    void nuncaActuaEnNombreDeCuentasSinAccesoNiLasReactiva(CapturedOutput salida) {
        seed.sembrar();
        usuarioService.crearUsuario("soporte.plataforma", "soporte@plataforma-real.example", "Soporte", "Plataforma",
                "OtraClave#2026", Rol.SUPER_ADMIN, null);
        long sc = empresa("SCA002");
        long laura = id("SELECT id FROM usuarios WHERE username = 'laura.torres'");
        comoEn("MVSA01", "carlos.mendoza", () -> usuarioService.cambiarEstadoTrabajador(laura,
                new CambioEstadoUsuarioDTO(EstadoUsuario.INACTIVO)));
        comoEn("SCA002", "soporte.plataforma", () -> empresaService.cambiarEstado(sc, new CambioEstadoEmpresaDTO(false)));
        Map<String, Integer> mvAntes = conteosDe(empresa("MVSA01"));
        Map<String, Integer> scAntes = conteosDe(sc);

        assertThat(seed.sembrar()).containsEntry("MVSA01", DemoSeedService.Resultado.ACTUALIZADA)
                .containsEntry("SCA002", DemoSeedService.Resultado.INCOMPLETA);

        assertThat(estadoDe("laura.torres")).isEqualTo("INACTIVO");
        assertThat(jdbc.queryForObject("SELECT activa FROM empresas WHERE id = ?", Boolean.class, sc)).isFalse();
        assertThat(conteosDe(empresa("MVSA01"))).isEqualTo(mvAntes);
        assertThat(conteosDe(sc)).isEqualTo(scAntes);
        assertThat(salida.getAll()).contains("SCA002 está desactivada; se omiten sus datos de negocio");
    }

    /**
     * Si la seed falla a mitad de una empresa, su transacción de negocio se deshace entera (la
     * empresa y sus cuentas ya están confirmadas) y la siguiente ejecución completa el resto,
     * con el mismo resultado que una ejecución limpia.
     */
    @Test
    void trasUnFalloParcialLaSiguienteEjecucionCompletaLoQueFalta() {
        // El espía está detrás del proxy de seguridad y transacciones: se programa sobre el objeto destino.
        DevolucionVentaService espia = AopTestUtils.getUltimateTargetObject(devolucionService);
        doThrow(new IllegalStateException("fallo simulado")).when(espia).devolver(anyLong(), any());

        assertThatThrownBy(seed::sembrar).hasMessageContaining("fallo simulado");
        long mv = empresa("MVSA01");
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE empresa_id = ?", mv)).as("alta confirmada").isEqualTo(2);
        assertThat(conteosDe(mv)).as("negocio deshecho").allSatisfy((tabla, n) -> {
            if (!tabla.equals("usuarios")) {
                assertThat(n).as(tabla).isZero();
            }
        });
        assertThat(contar("SELECT COUNT(*) FROM empresas WHERE demo")).as("las siguientes empresas no se tocaron").isEqualTo(1);

        reset(espia);
        assertThat(seed.sembrar()).containsEntry("MVSA01", DemoSeedService.Resultado.ACTUALIZADA)
                .containsEntry("LAS005", DemoSeedService.Resultado.CREADA);
        Map<String, Integer> reanudada = conteos();
        comprobarCoherencia();

        BaseDeDatosDePrueba.vaciar(jdbc);
        real = empresaReal();
        seed.sembrar();
        assertThat(conteos()).as("igual que una ejecución limpia").isEqualTo(reanudada);
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

    // ---------------------------------------------------------------- comprobaciones

    /**
     * Coherencia en todas las empresas: nadie cruza empresas, lo recibido cuadra con las
     * recepciones y el kárdex (cada movimiento guarda el saldo tras aplicarlo; el último es la
     * cantidad de {@code existencias}).
     */
    private void comprobarCoherencia() {
        assertThat(contar("SELECT COUNT(*) FROM solicitudes s JOIN usuarios u ON u.id = s.usuario_id "
                + "WHERE u.empresa_id <> s.empresa_id")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM ventas v JOIN usuarios u ON u.id = v.vendida_por_id "
                + "WHERE u.empresa_id <> v.empresa_id")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM recepciones r JOIN usuarios u ON u.id = r.recibida_por_id "
                + "WHERE u.empresa_id <> r.empresa_id")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM ordenes_compra o JOIN proveedores p ON p.id = o.proveedor_id "
                + "WHERE p.empresa_id <> o.empresa_id")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM detalles_orden_compra d WHERE d.cantidad_recibida > d.cantidad "
                + "OR d.cantidad_recibida <> (SELECT COALESCE(SUM(r.cantidad), 0) FROM detalles_recepcion r "
                + "WHERE r.detalle_orden_id = d.id)")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM movimientos_inventario WHERE tipo = 'RECEPCION'"))
                .isEqualTo(contar("SELECT COUNT(*) FROM detalles_recepcion"));
        assertThat(contar("SELECT COUNT(*) FROM movimientos_inventario WHERE tipo = 'VENTA'"))
                .isEqualTo(contar("SELECT COUNT(*) FROM detalles_venta"));

        Map<Long, Integer> saldos = new LinkedHashMap<>();
        List<String> errores = new ArrayList<>();
        jdbc.query("SELECT id, producto_id, cantidad, saldo_resultante FROM movimientos_inventario ORDER BY producto_id, id",
                rs -> {
                    long producto = rs.getLong(2);
                    int esperado = saldos.getOrDefault(producto, 0) + rs.getInt(3);
                    if (rs.getInt(4) != esperado || esperado < 0) {
                        errores.add("movimiento " + rs.getLong(1) + ": saldo " + rs.getInt(4) + ", esperado " + esperado);
                    }
                    saldos.put(producto, rs.getInt(4));
                });
        jdbc.query("SELECT producto_id, cantidad FROM existencias", rs -> {
            int esperado = saldos.getOrDefault(rs.getLong(1), 0);
            if (rs.getInt(2) != esperado) {
                errores.add("producto " + rs.getLong(1) + ": existencias " + rs.getInt(2) + ", último saldo " + esperado);
            }
        });
        assertThat(saldos).as("hay movimientos que comprobar").isNotEmpty();
        assertThat(errores).isEmpty();
    }

    // ---------------------------------------------------------------- utilidades

    private static Map<String, DemoSeedService.Resultado> todas(DemoSeedService.Resultado resultado) {
        Map<String, DemoSeedService.Resultado> todas = new LinkedHashMap<>();
        CODIGOS.forEach(c -> todas.put(c, resultado));
        return todas;
    }

    /** Ejecuta {@code accion} como {@code username} dentro de la empresa, igual que la seed. */
    private <T> T comoEn(String codigo, String username, Supplier<T> accion) {
        long empresaId = empresa(codigo);
        return TenantContextHolder.conEmpresa(empresaId, () -> transaccion.execute(estado -> DemoSeedService.como(
                usuarioRepository.findByUsername(username).orElseThrow(),
                empresaRepository.findById(empresaId).orElseThrow(), accion)));
    }

    /** Todas las filas de la empresa en todas las tablas, como texto (detecta cualquier cambio). */
    private String huellaDe(long empresaId) {
        StringBuilder huella = new StringBuilder(jdbc.queryForList("SELECT nombre || '|' || nit || '|' || activa || '|' || demo "
                + "FROM empresas WHERE id = ?", String.class, empresaId).toString());
        for (String tabla : TABLAS.subList(1, TABLAS.size())) {
            huella.append(tabla).append(jdbc.queryForList("SELECT * FROM " + tabla + " WHERE empresa_id = ? ORDER BY id",
                    empresaId));
        }
        return huella.toString();
    }

    private String estadoDe(String username) {
        return jdbc.queryForObject("SELECT estado FROM usuarios WHERE username = ?", String.class, username);
    }

    /** Filas de cada tabla de negocio de una empresa (más sus usuarios). */
    private Map<String, Integer> conteosDe(long empresaId) {
        Map<String, Integer> conteos = new LinkedHashMap<>();
        TABLAS.subList(1, TABLAS.size())
                .forEach(t -> conteos.put(t, contar("SELECT COUNT(*) FROM " + t + " WHERE empresa_id = ?", empresaId)));
        return conteos;
    }

    private long empresa(String codigo) {
        return id("SELECT id FROM empresas WHERE codigo = ? AND demo = TRUE", codigo);
    }

    private long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private int contar(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private Map<String, Integer> porEstado(String tabla, long empresaId) {
        Map<String, Integer> resultado = new LinkedHashMap<>();
        jdbc.query("SELECT estado, COUNT(*) FROM " + tabla + " WHERE empresa_id = ? GROUP BY estado", rs -> {
            resultado.put(rs.getString(1), rs.getInt(2));
        }, empresaId);
        return resultado;
    }

    private Map<String, Integer> conteos() {
        Map<String, Integer> conteos = new LinkedHashMap<>();
        TABLAS.forEach(t -> conteos.put(t, contar("SELECT COUNT(*) FROM " + t)));
        return conteos;
    }

    /** Stock por empresa y SKU (el SKU se repite entre empresas). */
    private Map<String, Integer> existencias() {
        Map<String, Integer> stock = new LinkedHashMap<>();
        jdbc.query("SELECT p.empresa_id, p.sku, e.cantidad FROM existencias e JOIN productos p ON p.id = e.producto_id "
                + "ORDER BY p.empresa_id, p.sku", rs -> {
                    stock.put(rs.getLong(1) + "/" + rs.getString(2), rs.getInt(3));
                });
        return stock;
    }

    /** Estado de cada usuario, solicitud, orden y venta (detecta cambios sin filas nuevas). */
    private Map<String, String> estados() {
        Map<String, String> estados = new LinkedHashMap<>();
        jdbc.query("SELECT username, estado, password_hash FROM usuarios ORDER BY username", rs -> {
            estados.put("u/" + rs.getString(1), rs.getString(2) + "/" + rs.getString(3));
        });
        jdbc.query("SELECT id, estado FROM solicitudes ORDER BY id", rs -> {
            estados.put("s/" + rs.getLong(1), rs.getString(2));
        });
        jdbc.query("SELECT id, estado FROM ordenes_compra ORDER BY id", rs -> {
            estados.put("o/" + rs.getLong(1), rs.getString(2));
        });
        jdbc.query("SELECT id, estado FROM ventas ORDER BY id", rs -> {
            estados.put("v/" + rs.getLong(1), rs.getString(2));
        });
        jdbc.query("SELECT id, precio_venta FROM productos ORDER BY id", rs -> {
            estados.put("p/" + rs.getLong(1), String.valueOf(rs.getObject(2)));
        });
        return estados;
    }

    /** Una instancia de la seed con los beans del contexto y otra contraseña (el resto, idéntico). */
    private DemoSeedService seedConPassword(String password) throws Exception {
        Constructor<?> constructor = DemoSeedService.class.getConstructors()[0];
        Object[] argumentos = Arrays.stream(constructor.getParameterTypes())
                .map(tipo -> tipo == String.class ? password : contexto.getBean(tipo))
                .toArray();
        return (DemoSeedService) constructor.newInstance(argumentos);
    }
}
