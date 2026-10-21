package com.unisen.sgp.config.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisen.sgp.exception.EmpresaNoSeleccionadaException;
import com.unisen.sgp.model.dto.CambioEstadoEmpresaDTO;
import com.unisen.sgp.model.dto.CambioEstadoUsuarioDTO;
import com.unisen.sgp.model.dto.LineaOrdenCompraDTO;
import com.unisen.sgp.model.dto.OrdenCompraEdicionDTO;
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
import com.unisen.sgp.service.OrdenCompraService;
import com.unisen.sgp.service.ProductoService;
import com.unisen.sgp.service.ProveedorService;
import com.unisen.sgp.service.UsuarioService;
import com.unisen.sgp.support.BaseDeDatosDePrueba;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Escenarios completos de la seed (DEMO01 y DEMO02): estados de usuarios e invitaciones, catálogo
 * con bajas y stock en el mínimo, solicitudes en todos sus estados, órdenes desde solicitudes y
 * manuales, recepciones con incidencias y su reflejo en el inventario. También: idempotencia, una
 * base que ya tenía los datos demo sin escenarios, el aislamiento entre DEMO01 y DEMO02 (mismo NIT,
 * SKU y código de barras) y una empresa real que comparte esas claves y no se toca.
 */
@SpringBootTest(properties = "app.demo.ejecutar-al-arrancar=false")
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
@ExtendWith(OutputCaptureExtension.class)
class EscenariosDemoTest {

    private static final String PASSWORD = DemoSeedTest.passwordAleatoria();
    private static final List<String> ESCENARIOS = List.of("DEMO01", "DEMO02");
    private static final String CODIGO_BARRAS_COMPARTIDO = DatosDemo.ean13(9, 1);

    @DynamicPropertySource
    static void passwordDemo(DynamicPropertyRegistry registro) {
        registro.add("app.demo.password", () -> PASSWORD);
    }

    @Autowired
    private DemoSeedService seed;
    @MockitoSpyBean
    private EscenariosDemo escenarios;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private ProveedorService proveedorService;
    @Autowired
    private ProductoService productoService;
    @Autowired
    private OrdenCompraService ordenService;
    @Autowired
    private EmpresaService empresaService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private TransactionTemplate transaccion;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private long real;

    /** Una empresa real con el mismo NIT de proveedor y el mismo SKU y código de barras que los escenarios. */
    @BeforeEach
    void baseConUnaEmpresaReal() {
        BaseDeDatosDePrueba.vaciar(jdbc);
        Empresa empresa = empresaRepository.save(new Empresa("Suministros Reales S.A.S.", "901555444-1", "REAL01",
                CodigosInvitacion.generar()));
        real = empresa.getId();
        Usuario gerente = usuarioService.crearUsuario("gerente.suministros", "gerente@suministros-real.com", "Gerente",
                "Real", "OtraClave#2026", Rol.ADMINISTRADOR_COMERCIAL, empresa);
        DemoSeedService.como(gerente, empresa, () -> {
            long proveedor = proveedorService.crear(new ProveedorRequestDTO(DatosDemo.PROVEEDOR_ESCENARIOS.nit(),
                    "Proveedor Real Ltda.", "compras@proveedor-real.com", null, null)).id();
            return productoService.crear(new ProductoRequestDTO("ESC-COM-01", CODIGO_BARRAS_COMPARTIDO, "Producto real",
                    null, new BigDecimal("1000"), 1, proveedor)).id();
        });
    }

    @Test
    void creaTodosLosEscenariosYUnaSegundaEjecucionNoCambiaNada() {
        assertThat(seed.sembrar()).doesNotContainValue(DemoSeedService.Resultado.INCOMPLETA);

        for (String codigo : ESCENARIOS) {
            comprobarEscenarios(codigo);
        }
        comprobarRelaciones();
        comprobarEmpresaRealIntacta();
        for (String codigo : List.of("DEMO03", "DEMO04", "DEMO05")) {
            long id = empresa(codigo);
            assertThat(contar("SELECT COUNT(*) FROM productos WHERE empresa_id = ? AND sku LIKE 'ESC-%'", id)).as(codigo).isZero();
            assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE empresa_id = ? AND estado <> 'ACTIVO'", id)).as(codigo).isZero();
        }

        Map<String, Integer> conteos = conteos();
        Map<String, Integer> stock = existencias();
        Map<String, String> estados = estados();
        seed.sembrar();
        comprobarKardex();
        assertThat(conteos()).isEqualTo(conteos);
        assertThat(existencias()).isEqualTo(stock);
        assertThat(estados()).isEqualTo(estados);
        comprobarEmpresaRealIntacta();
    }

    @Test
    void seAnadenSobreUnaBaseQueYaTieneLosDatosDemo() {
        // Base sembrada antes de existir los escenarios: empresas demo con sus órdenes, ventas, etc.
        doNothing().when(escenarios).sembrar(any(), any(), any(), anyList(), anyString());
        seed.sembrar();
        long demo01 = empresa("DEMO01");
        assertThat(contar("SELECT COUNT(*) FROM ordenes_compra WHERE empresa_id = ?", demo01)).isEqualTo(3);
        assertThat(contar("SELECT COUNT(*) FROM solicitudes")).isZero();
        Map<String, Integer> base = new LinkedHashMap<>();
        for (String tabla : List.of("ventas", "detalles_venta", "devoluciones_venta", "cambios_precio", "empresas")) {
            base.put(tabla, contar("SELECT COUNT(*) FROM " + tabla));
        }
        Map<String, Integer> stockBase = existencias();
        Map<String, String> estadosBase = estados();

        // Las órdenes que ya existen no impiden crear los escenarios.
        reset(escenarios);
        seed.sembrar();

        for (String codigo : ESCENARIOS) {
            comprobarEscenarios(codigo);
        }
        comprobarRelaciones();
        base.forEach((tabla, n) -> assertThat(contar("SELECT COUNT(*) FROM " + tabla)).as(tabla).isEqualTo(n));
        // Los datos previos no cambian: stock, usuarios, órdenes e invitaciones siguen igual.
        Map<String, Integer> stockDespues = existencias();
        stockBase.forEach((clave, cantidad) -> assertThat(stockDespues).containsEntry(clave, cantidad));
        Map<String, String> estadosDespues = estados();
        estadosBase.forEach((clave, estado) -> assertThat(estadosDespues).containsEntry(clave, estado));
        comprobarEmpresaRealIntacta();
    }

    @Test
    void cadaEmpresaVeSoloLoSuyoYLosEstadosDecidenElAcceso() throws Exception {
        seed.sembrar();
        long demo01 = empresa("DEMO01");
        long demo02 = empresa("DEMO02");

        // Cuentas activas: entran. Pendiente, rechazada, inactiva y las de una empresa desactivada: no.
        String admin1 = token("demo1.admin");
        String admin2 = token("demo2.admin");
        String ana = token("demo1.ana");
        token("demo1.invitado");
        token("demo2.invitado");
        for (int n = 1; n <= 2; n++) {
            rechazado("demo" + n + ".pendiente", "PENDIENTE");
            rechazado("demo" + n + ".rechazado", "RECHAZADO");
            rechazado("demo" + n + ".inactivo", "INACTIVO");
        }
        rechazado("demo6.admin", "EMPRESA_INACTIVA");
        rechazado("demo6.carlos", "EMPRESA_INACTIVA");
        login("demo1.admin", PASSWORD + "x").andExpect(status().isUnauthorized());
        login("gerente.suministros", PASSWORD).andExpect(status().isUnauthorized());

        // Mismo código de barras en las tres empresas: cada una encuentra el suyo.
        assertThat(productoPorCodigo(admin1)).isEqualTo(id("SELECT id FROM productos WHERE empresa_id = ? AND sku = 'ESC-COM-01'", demo01));
        assertThat(productoPorCodigo(admin2)).isEqualTo(id("SELECT id FROM productos WHERE empresa_id = ? AND sku = 'ESC-COM-01'", demo02));

        // Solicitudes: el administrador ve las 6 de su empresa; las de la otra empresa no existen para él.
        getJson("/api/v1/solicitudes?size=50", admin1).andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(6));
        long solicitudDemo02 = id("SELECT MIN(id) FROM solicitudes WHERE empresa_id = ?", demo02);
        getJson("/api/v1/solicitudes/" + solicitudDemo02, admin1).andExpect(status().isNotFound());
        long ordenDemo02 = id("SELECT MIN(id) FROM ordenes_compra WHERE empresa_id = ?", demo02);
        getJson("/api/v1/ordenes-compra/" + ordenDemo02, admin1).andExpect(status().isNotFound());
        getJson("/api/v1/ordenes-compra?size=50", admin1).andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(8));
        getJson("/api/auth/invitaciones?size=50", admin1).andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4));

        // El trabajador ve solo las suyas (3) y no las de su compañero.
        getJson("/api/v1/solicitudes?size=50", ana).andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3));
        long deLuis = id("SELECT MIN(s.id) FROM solicitudes s JOIN usuarios u ON u.id = s.usuario_id "
                + "WHERE u.username = 'demo1.luis'");
        getJson("/api/v1/solicitudes/" + deLuis, ana).andExpect(status().isNotFound());
    }

    /** I1: ni en nombre de una cuenta sin acceso ni en una empresa desactivada; nada se reactiva. */
    @Test
    void nuncaActuaEnNombreDeCuentasSinAccesoNiLasReactiva(CapturedOutput salida) {
        doNothing().when(escenarios).sembrar(any(), any(), any(), anyList(), anyString());
        seed.sembrar();
        usuarioService.crearUsuario("soporte.plataforma", "soporte@plataforma-real.com", "Soporte", "Plataforma",
                "OtraClave#2026", Rol.SUPER_ADMIN, null);
        long ana = id("SELECT id FROM usuarios WHERE username = 'demo1.ana'");
        long admin2 = id("SELECT id FROM usuarios WHERE username = 'demo2.admin'");
        comoEn("DEMO01", "demo1.admin", () -> usuarioService.cambiarEstadoTrabajador(ana,
                new CambioEstadoUsuarioDTO(EstadoUsuario.INACTIVO)));
        comoEn("DEMO02", "soporte.plataforma", () -> empresaService.cambiarEstadoAdministrador(empresa("DEMO02"), admin2,
                new CambioEstadoUsuarioDTO(EstadoUsuario.INACTIVO)));
        comoEn("DEMO04", "soporte.plataforma", () -> empresaService.cambiarEstado(empresa("DEMO04"),
                new CambioEstadoEmpresaDTO(false)));
        Map<String, Integer> demo02 = conteosDe(empresa("DEMO02"));
        Map<String, Integer> demo04 = conteosDe(empresa("DEMO04"));
        long ventasDeAna = contar("SELECT COUNT(*) FROM ventas WHERE vendida_por_id = ?", ana);

        reset(escenarios);
        assertThat(seed.sembrar()).containsEntry("DEMO01", DemoSeedService.Resultado.ACTUALIZADA)
                .containsEntry("DEMO02", DemoSeedService.Resultado.INCOMPLETA)
                .containsEntry("DEMO04", DemoSeedService.Resultado.INCOMPLETA);

        // Nada se reactivó.
        assertThat(estadoDe("demo1.ana")).isEqualTo("INACTIVO");
        assertThat(estadoDe("demo2.admin")).isEqualTo("INACTIVO");
        assertThat(jdbc.queryForObject("SELECT activa FROM empresas WHERE codigo = 'DEMO04'", Boolean.class)).isFalse();
        // DEMO01: los escenarios de Luis y del administrador sí; ninguno a nombre de Ana.
        long demo01 = empresa("DEMO01");
        assertThat(contar("SELECT COUNT(*) FROM solicitudes WHERE usuario_id = ?", ana)).isZero();
        assertThat(contar("SELECT COUNT(*) FROM recepciones WHERE recibida_por_id = ?", ana)).isZero();
        assertThat(contar("SELECT COUNT(*) FROM ventas WHERE vendida_por_id = ?", ana)).isEqualTo(ventasDeAna);
        assertThat(porEstado("solicitudes", demo01)).containsExactlyInAnyOrderEntriesOf(Map.of("PENDIENTE", 1, "APROBADA", 2));
        assertThat(contar("SELECT COUNT(*) FROM ordenes_compra WHERE empresa_id = ? AND observaciones = ? "
                + "AND estado = 'RECIBIDA'", demo01, EscenariosDemo.ORDEN_EXCEDENTE)).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM codigos_invitacion WHERE empresa_id = ?", demo01)).isEqualTo(4);
        // DEMO02 (administrador sin acceso) y DEMO04 (desactivada): intactas.
        assertThat(conteosDe(empresa("DEMO02"))).isEqualTo(demo02);
        assertThat(conteosDe(empresa("DEMO04"))).isEqualTo(demo04);
        assertThat(salida.getAll())
                .contains("demo1.ana no está activo; se omite en DEMO01 la solicitud")
                .contains("DEMO02 tiene a su administrador demo2.admin sin acceso; se omiten sus datos de negocio")
                .contains("DEMO04 está desactivada; se omiten sus datos de negocio");
        comprobarRelaciones();

        // Cuando la causa desaparece (alguien reactiva a Ana), la siguiente ejecución completa lo que faltaba.
        comoEn("DEMO01", "demo1.admin", () -> usuarioService.cambiarEstadoTrabajador(ana,
                new CambioEstadoUsuarioDTO(EstadoUsuario.ACTIVO)));
        seed.sembrar();
        comprobarEscenarios("DEMO01");
        comprobarRelaciones();
    }

    /** M2: un username o correo ocupado por otra cuenta omite solo su escenario y no toca esa cuenta. */
    @Test
    void unaCuentaOcupadaOmiteSoloSuEscenarioSinTocarla(CapturedOutput salida) {
        Empresa empresaReal = empresaRepository.findById(real).orElseThrow();
        usuarioService.crearUsuario("demo1.pendiente", "pendiente@suministros-real.com", "Pedro", "Real", "OtraClave#2026",
                Rol.TRABAJADOR, empresaReal);
        usuarioService.crearUsuario("demo1.invitado", "invitado@suministros-real.com", "Inés", "Real", "OtraClave#2026",
                Rol.TRABAJADOR, empresaReal);
        // Username libre, pero con el correo que la seed daría a demo2.rechazado.
        usuarioService.crearUsuario("otra.persona", DemoSeedService.correo(new DatosDemo.Persona("demo2.rechazado", "R", "R"),
                "DEMO02"), "Otra", "Persona", "OtraClave#2026", Rol.TRABAJADOR, empresaReal);
        String antes = cuentas(real);

        seed.sembrar();
        seed.sembrar();

        assertThat(cuentas(real)).as("las cuentas existentes no cambian").isEqualTo(antes);
        long demo01 = empresa("DEMO01");
        long demo02 = empresa("DEMO02");
        assertThat(contar("SELECT COUNT(*) FROM codigos_invitacion WHERE empresa_id = ?", demo01)).as("dependen del invitado")
                .isZero();
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE empresa_id = ? AND estado <> 'ACTIVO'", demo01)).isEqualTo(2);
        assertThat(contar("SELECT COUNT(*) FROM solicitudes WHERE empresa_id = ?", demo01)).isEqualTo(6);
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE username = 'demo2.rechazado'")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM codigos_invitacion WHERE empresa_id = ?", demo02)).isEqualTo(4);
        assertThat(salida.getAll())
                .contains("el username o el correo de demo1.pendiente ya los usa otra cuenta; se omite en DEMO01 el "
                        + "escenario: usuario pendiente")
                .contains("de demo1.invitado ya los usa otra cuenta; se omite en DEMO01 el escenario: invitaciones")
                .contains("de demo2.rechazado ya los usa otra cuenta; se omite en DEMO02 el escenario: usuario rechazado");
        comprobarRelaciones();
    }

    /** M3: un SKU que ya existe (de escenarios o de la seed base) conserva su precio y su stock. */
    @Test
    void unSkuQueYaExisteConservaSuPrecioYSuStock() {
        // DEMO05 tal como la dejaría una ejecución anterior, con un producto base creado a mano sin precio ni stock.
        Empresa demo05 = new Empresa("Deportes Gol de Oro (demo)", "900990005-5", "DEMO05", CodigosInvitacion.generar());
        demo05.marcarDemo();
        Empresa guardada = empresaRepository.save(demo05);
        Usuario admin5 = usuarioService.crearUsuario("demo5.admin", "demo5-admin@demo05.sgp-demo.test", "Lucía",
                "Navarro Demo", PASSWORD, Rol.ADMINISTRADOR_COMERCIAL, guardada);
        long balon = DemoSeedService.como(admin5, guardada, () -> {
            long proveedor = proveedorService.crear(new ProveedorRequestDTO("800990501-1", "Textiles Deportivos Irreales (demo)",
                    "ventas@irreales-demo.test", null, null)).id();
            return productoService.crear(new ProductoRequestDTO("DEP-BAL-F5", "BAL-MANUAL-1", "Balón creado a mano", null,
                    new BigDecimal("40000"), 1, proveedor)).id();
        });
        // DEMO01 sembrada antes de los escenarios y con un ESC-MIN-IG creado a mano.
        doNothing().when(escenarios).sembrar(any(), any(), any(), anyList(), anyString());
        seed.sembrar();
        long demo01 = empresa("DEMO01");
        long proveedorBase = id("SELECT id FROM proveedores WHERE empresa_id = ? AND nit = '800990101-1'", demo01);
        long manual = comoEn("DEMO01", "demo1.admin", () -> productoService.crear(new ProductoRequestDTO("ESC-MIN-IG",
                "MIN-MANUAL-1", "Producto creado a mano", null, new BigDecimal("4000"), 3, proveedorBase)).id());

        reset(escenarios);
        seed.sembrar();
        seed.sembrar();

        for (long producto : List.of(balon, manual)) {
            assertThat(contar("SELECT COUNT(*) FROM movimientos_inventario WHERE producto_id = ? AND tipo = 'AJUSTE'", producto))
                    .as("sin inventario inicial: %d", producto).isZero();
            assertThat(contar("SELECT COUNT(*) FROM productos WHERE id = ? AND precio_venta IS NULL AND nombre LIKE '%a mano'",
                    producto)).as("conserva nombre y precio: %d", producto).isEqualTo(1);
        }
        assertThat(stockYMinimo(demo01, "ESC-MIN-IG")).containsExactly(0, 3);
        assertThat(stockYMinimo(empresa("DEMO02"), "ESC-MIN-IG")).as("creado por la seed").containsExactly(6, 6);
        assertThat(contar("SELECT COUNT(*) FROM movimientos_inventario WHERE empresa_id = ? AND tipo = 'AJUSTE'",
                empresa("DEMO05"))).as("los demás productos de DEMO05 sí").isPositive();
        comprobarRelaciones();
    }

    /** M4: editar las observaciones del borrador manual no hace que se cree otro. */
    @Test
    void editarElBorradorManualNoLoDuplica() {
        seed.sembrar();
        long demo01 = empresa("DEMO01");
        long borrador = id("SELECT id FROM ordenes_compra WHERE empresa_id = ? AND observaciones = ?", demo01,
                EscenariosDemo.ORDEN_BORRADOR);
        long producto = id("SELECT id FROM productos WHERE empresa_id = ? AND sku = 'ESC-COM-02'", demo01);
        comoEn("DEMO01", "demo1.admin", () -> ordenService.actualizar(borrador, new OrdenCompraEdicionDTO(
                "Observaciones cambiadas a mano", List.of(new LineaOrdenCompraDTO(producto, 7, new BigDecimal("7000"))))));
        int ordenes = contar("SELECT COUNT(*) FROM ordenes_compra");

        seed.sembrar();

        assertThat(contar("SELECT COUNT(*) FROM ordenes_compra")).isEqualTo(ordenes);
        assertThat(contar("SELECT COUNT(*) FROM ordenes_compra WHERE observaciones = ? AND empresa_id = ?",
                EscenariosDemo.ORDEN_BORRADOR, demo01)).isZero();
        assertThat(jdbc.queryForObject("SELECT estado || '|' || observaciones FROM ordenes_compra WHERE id = ?", String.class,
                borrador)).isEqualTo("BORRADOR|Observaciones cambiadas a mano");
    }

    /** M6: sin contexto de empresa, los escenarios fallan antes de buscar nada por NIT o SKU. */
    @Test
    void losEscenariosExigenUnContextoDeEmpresa() {
        Map<String, Integer> antes = conteos();
        assertThat(TenantContextHolder.obtener()).isEmpty();
        assertThatThrownBy(() -> escenarios.sembrar(DatosDemo.EMPRESAS.get(0), null, null, List.of(), PASSWORD))
                .isInstanceOf(EmpresaNoSeleccionadaException.class);
        assertThat(conteos()).isEqualTo(antes);
    }

    // ---------------------------------------------------------------- comprobaciones

    private void comprobarEscenarios(String codigo) {
        long id = empresa(codigo);
        String n = codigo.substring(5);

        // Usuarios por estado (todos trabajadores de esta empresa).
        Map<String, String> estados = new LinkedHashMap<>();
        for (String sufijo : List.of("pendiente", "rechazado", "inactivo", "invitado")) {
            estados.put(sufijo, jdbc.queryForObject("SELECT estado FROM usuarios WHERE username = ? AND empresa_id = ? "
                    + "AND rol = 'TRABAJADOR'", String.class, "demo" + n + "." + sufijo, id));
        }
        assertThat(estados).as(codigo).containsExactly(Map.entry("pendiente", "PENDIENTE"),
                Map.entry("rechazado", "RECHAZADO"), Map.entry("inactivo", "INACTIVO"), Map.entry("invitado", "ACTIVO"));

        // Invitaciones: vigente (720 h), de 1 h, revocada y usada por el invitado.
        assertThat(contar("SELECT COUNT(*) FROM codigos_invitacion WHERE empresa_id = ?", id)).as(codigo).isEqualTo(4);
        assertThat(contar("SELECT COUNT(*) FROM codigos_invitacion i JOIN usuarios u ON u.id = i.usado_por_id "
                + "WHERE i.empresa_id = ? AND i.usado = TRUE AND u.username = ?", id, "demo" + n + ".invitado")).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM codigos_invitacion WHERE empresa_id = ? AND fecha_revocacion IS NOT NULL "
                + "AND revocado_por_id IS NOT NULL AND usado = FALSE", id)).isEqualTo(1);
        List<Long> horasVigentes = new ArrayList<>();
        jdbc.query("SELECT creado_en, fecha_expiracion FROM codigos_invitacion WHERE empresa_id = ? AND usado = FALSE "
                + "AND fecha_revocacion IS NULL ORDER BY fecha_expiracion", rs -> {
                    horasVigentes.add(Duration.between(rs.getObject(1, OffsetDateTime.class),
                            rs.getObject(2, OffsetDateTime.class)).toHours());
                }, id);
        assertThat(horasVigentes).as(codigo).containsExactly(1L, 720L);

        // Catálogo: proveedor y producto dados de baja; stock en el mínimo y por debajo.
        DatosDemo.Escenarios escenarios = DatosDemo.EMPRESAS.stream().filter(d -> d.codigo().equals(codigo)).findFirst()
                .orElseThrow().escenarios();
        assertThat(jdbc.queryForObject("SELECT activo FROM proveedores WHERE empresa_id = ? AND nit = ?", Boolean.class, id,
                escenarios.proveedorDeBaja().nit())).as(codigo + ": proveedor dado de baja").isFalse();
        assertThat(jdbc.queryForObject("SELECT activo FROM proveedores WHERE empresa_id = ? AND nit = ?", Boolean.class, id,
                DatosDemo.PROVEEDOR_ESCENARIOS.nit())).isTrue();
        assertThat(jdbc.queryForObject("SELECT activo FROM productos WHERE empresa_id = ? AND sku = 'ESC-BAJA-01'",
                Boolean.class, id)).isFalse();
        assertThat(jdbc.queryForObject("SELECT codigo_barras FROM productos WHERE empresa_id = ? AND sku = 'ESC-COM-01'",
                String.class, id)).isEqualTo(CODIGO_BARRAS_COMPARTIDO);
        assertThat(stockYMinimo(id, "ESC-MIN-IG")).as(codigo + ": igual al mínimo").containsExactly(6, 6);
        assertThat(stockYMinimo(id, "ESC-MIN-BA")).as(codigo + ": bajo el mínimo").containsExactly(2, 10);
        assertThat(stockYMinimo(id, "ESC-COM-01")).as(codigo + ": 40 iniciales + 5 recibidas").containsExactly(45, 5);
        assertThat(stockYMinimo(id, "ESC-COM-02")).as(codigo + ": 30 iniciales + 12 recibidas").containsExactly(42, 5);

        // Solicitudes en todos sus estados; una aprobada sin orden y dos ya procesadas.
        assertThat(porEstado("solicitudes", id)).as(codigo)
                .containsExactlyInAnyOrderEntriesOf(Map.of("PENDIENTE", 2, "RECHAZADA", 1, "APROBADA", 3));
        assertThat(contar("SELECT COUNT(*) FROM solicitudes s WHERE s.empresa_id = ? AND s.estado = 'APROBADA' "
                + "AND NOT EXISTS (SELECT 1 FROM ordenes_compra o WHERE o.solicitud_id = s.id)", id)).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM solicitudes WHERE empresa_id = ? AND estado <> 'PENDIENTE' "
                + "AND (revisado_por_id IS NULL OR fecha_revision IS NULL)", id)).isZero();
        assertThat(contar("SELECT COUNT(*) FROM solicitudes WHERE empresa_id = ? AND estado = 'RECHAZADA' "
                + "AND comentario_revision IS NOT NULL", id)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT estado FROM ordenes_compra WHERE empresa_id = ? AND solicitud_id IS NOT NULL",
                String.class, id)).containsExactlyInAnyOrder("BORRADOR", "RECIBIDA_PARCIALMENTE");

        // Órdenes: 3 base + 2 desde solicitudes + 3 manuales.
        assertThat(porEstado("ordenes_compra", id)).as(codigo).containsExactlyInAnyOrderEntriesOf(Map.of(
                "BORRADOR", 2, "EMITIDA", 1, "RECIBIDA_PARCIALMENTE", 2, "RECIBIDA", 2, "CANCELADA", 1));
        assertThat(contar("SELECT COUNT(*) FROM ordenes_compra WHERE empresa_id = ? AND estado = 'CANCELADA' "
                + "AND motivo_cancelacion IS NOT NULL AND emitida_en IS NOT NULL", id)).isEqualTo(1);
        // Entrega programada (a futuro) de la orden de la solicitud recibida en parte.
        LocalDate fecha = jdbc.queryForObject("SELECT p.fecha_estimada FROM ordenes_compra o "
                + "JOIN programaciones_entrega p ON p.id = o.programacion_entrega_id "
                + "WHERE o.empresa_id = ? AND o.solicitud_id IS NOT NULL AND o.estado = 'RECIBIDA_PARCIALMENTE'",
                LocalDate.class, id);
        assertThat(fecha).isAfter(LocalDate.now());

        // Incidencias: daño y faltante en la parcial, excedente en la completa.
        Map<String, Integer> incidencias = new LinkedHashMap<>();
        jdbc.query("SELECT i.tipo, i.cantidad, o.estado FROM incidencias_recepcion i JOIN recepciones r ON r.id = i.recepcion_id "
                + "JOIN ordenes_compra o ON o.id = r.orden_id WHERE i.empresa_id = ? ORDER BY i.tipo", rs -> {
                    incidencias.put(rs.getString(1) + "@" + rs.getString(3), rs.getInt(2));
                }, id);
        assertThat(incidencias).as(codigo).containsExactlyInAnyOrderEntriesOf(Map.of(
                "DANADO@RECIBIDA_PARCIALMENTE", 1, "FALTANTE@RECIBIDA_PARCIALMENTE", 2, "EXCEDENTE@RECIBIDA", 2));
        assertThat(contar("SELECT COUNT(*) FROM recepciones WHERE empresa_id = ?", id)).isEqualTo(4);
    }

    /** Coherencia entre solicitudes, órdenes, recepciones, incidencias y movimientos, en todas las empresas. */
    private void comprobarRelaciones() {
        // Las órdenes de una solicitud son de su empresa, de una solicitud aprobada y con sus mismas cantidades.
        assertThat(contar("SELECT COUNT(*) FROM ordenes_compra o JOIN solicitudes s ON s.id = o.solicitud_id "
                + "WHERE o.empresa_id <> s.empresa_id OR s.estado <> 'APROBADA'")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM detalles_orden_compra d JOIN ordenes_compra o ON o.id = d.orden_id "
                + "WHERE o.solicitud_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM detalles_solicitud ds "
                + "WHERE ds.solicitud_id = o.solicitud_id AND ds.producto_id = d.producto_id AND ds.cantidad = d.cantidad)"))
                .isZero();
        // Lo recibido en cada línea es la suma de sus recepciones y no supera lo pedido.
        assertThat(contar("SELECT COUNT(*) FROM detalles_orden_compra d WHERE d.cantidad_recibida > d.cantidad "
                + "OR d.cantidad_recibida <> (SELECT COALESCE(SUM(r.cantidad), 0) FROM detalles_recepcion r "
                + "WHERE r.detalle_orden_id = d.id)")).isZero();
        // Cada línea recibida generó un movimiento RECEPCION del mismo producto, cantidad y empresa.
        assertThat(contar("SELECT COUNT(*) FROM detalles_recepcion r WHERE NOT EXISTS (SELECT 1 FROM movimientos_inventario m "
                + "JOIN detalles_orden_compra d ON d.id = r.detalle_orden_id WHERE m.detalle_recepcion_id = r.id "
                + "AND m.tipo = 'RECEPCION' AND m.cantidad = r.cantidad AND m.producto_id = d.producto_id "
                + "AND m.empresa_id = r.empresa_id)")).isZero();
        // Las incidencias no mueven inventario: ningún movimiento sin su línea recibida.
        assertThat(contar("SELECT COUNT(*) FROM movimientos_inventario WHERE tipo = 'RECEPCION'"))
                .isEqualTo(contar("SELECT COUNT(*) FROM detalles_recepcion"));
        assertThat(contar("SELECT COUNT(*) FROM incidencias_recepcion i JOIN recepciones r ON r.id = i.recepcion_id "
                + "JOIN ordenes_compra o ON o.id = r.orden_id WHERE i.empresa_id <> r.empresa_id OR r.empresa_id <> o.empresa_id"))
                .isZero();
        // Nadie cruza empresas: solicitante, revisor, receptor y creador de invitaciones son de la misma empresa.
        assertThat(contar("SELECT COUNT(*) FROM solicitudes s JOIN usuarios u ON u.id = s.usuario_id "
                + "WHERE u.empresa_id <> s.empresa_id")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM solicitudes s JOIN usuarios u ON u.id = s.revisado_por_id "
                + "WHERE u.empresa_id <> s.empresa_id")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM recepciones r JOIN usuarios u ON u.id = r.recibida_por_id "
                + "WHERE u.empresa_id <> r.empresa_id")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM codigos_invitacion i JOIN usuarios u ON u.id = i.usuario_creador_id "
                + "WHERE u.empresa_id <> i.empresa_id")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM ordenes_compra o JOIN proveedores p ON p.id = o.proveedor_id "
                + "WHERE p.empresa_id <> o.empresa_id")).isZero();
        comprobarKardex();
    }

    /**
     * Regla del kárdex (MovimientoInventario + Existencia.aplicar/entrada): cada movimiento guarda
     * en {@code saldo_resultante} las existencias del producto justo después de aplicarlo. Por
     * producto y en orden de registro (id): el primero parte de 0, cada saldo es el anterior más la
     * cantidad (con signo) y el último es la cantidad de {@code existencias}; sin movimientos, 0.
     */
    private void comprobarKardex() {
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

    /** La empresa real (con el mismo NIT y SKU que los escenarios) sigue exactamente como estaba. */
    private void comprobarEmpresaRealIntacta() {
        assertThat(jdbc.queryForObject("SELECT nombre || '|' || activa || '|' || demo FROM empresas WHERE id = ?",
                String.class, real)).isEqualToIgnoringCase("Suministros Reales S.A.S.|true|false");
        assertThat(jdbc.queryForList("SELECT razon_social FROM proveedores WHERE empresa_id = ?", String.class, real))
                .containsExactly("Proveedor Real Ltda.");
        assertThat(jdbc.queryForList("SELECT nombre FROM productos WHERE empresa_id = ?", String.class, real))
                .containsExactly("Producto real");
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE empresa_id = ?", real)).isEqualTo(1);
        for (String tabla : List.of("movimientos_inventario", "solicitudes", "ordenes_compra", "recepciones",
                "codigos_invitacion", "ventas")) {
            assertThat(contar("SELECT COUNT(*) FROM " + tabla + " WHERE empresa_id = ?", real)).as(tabla).isZero();
        }
    }

    // ---------------------------------------------------------------- utilidades

    /** Ejecuta {@code accion} como {@code username} dentro de la empresa, igual que la seed. */
    private <T> T comoEn(String codigo, String username, Supplier<T> accion) {
        long empresaId = empresa(codigo);
        return TenantContextHolder.conEmpresa(empresaId, () -> transaccion.execute(estado -> DemoSeedService.como(
                usuarioRepository.findByUsername(username).orElseThrow(),
                empresaRepository.findById(empresaId).orElseThrow(), accion)));
    }

    private String estadoDe(String username) {
        return jdbc.queryForObject("SELECT estado FROM usuarios WHERE username = ?", String.class, username);
    }

    /** Filas de cada tabla de negocio de una empresa. */
    private Map<String, Integer> conteosDe(long empresaId) {
        Map<String, Integer> conteos = new LinkedHashMap<>();
        DemoSeedTest.TABLAS.stream().filter(t -> !t.equals("empresas"))
                .forEach(t -> conteos.put(t, contar("SELECT COUNT(*) FROM " + t + " WHERE empresa_id = ?", empresaId)));
        return conteos;
    }

    /** Username, estado, contraseña cifrada y correo de cada cuenta de la empresa. */
    private String cuentas(long empresaId) {
        return jdbc.queryForList("SELECT username || '|' || estado || '|' || password_hash || '|' || email FROM usuarios "
                + "WHERE empresa_id = ? ORDER BY username", String.class, empresaId).toString();
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

    private List<Integer> stockYMinimo(long empresaId, String sku) {
        return jdbc.queryForObject("SELECT e.cantidad, p.stock_minimo FROM existencias e JOIN productos p ON p.id = e.producto_id "
                + "WHERE p.empresa_id = ? AND p.sku = ?", (rs, i) -> List.of(rs.getInt(1), rs.getInt(2)), empresaId, sku);
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
        DemoSeedTest.TABLAS.forEach(t -> conteos.put(t, contar("SELECT COUNT(*) FROM " + t)));
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

    /** Estado de cada usuario, solicitud, orden e invitación (detecta cambios sin filas nuevas). */
    private Map<String, String> estados() {
        Map<String, String> estados = new LinkedHashMap<>();
        jdbc.query("SELECT username, estado FROM usuarios ORDER BY username", rs -> {
            estados.put("u/" + rs.getString(1), rs.getString(2));
        });
        jdbc.query("SELECT id, estado FROM solicitudes ORDER BY id", rs -> {
            estados.put("s/" + rs.getLong(1), rs.getString(2));
        });
        jdbc.query("SELECT id, estado, programacion_entrega_id FROM ordenes_compra ORDER BY id", rs -> {
            estados.put("o/" + rs.getLong(1), rs.getString(2) + "/" + rs.getObject(3));
        });
        jdbc.query("SELECT id, usado, fecha_revocacion FROM codigos_invitacion ORDER BY id", rs -> {
            estados.put("i/" + rs.getLong(1), rs.getBoolean(2) + "/" + (rs.getObject(3) != null));
        });
        return estados;
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(DemoSeedTest.login(username, password)));
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

    private ResultActions getJson(String url, String token) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", "Bearer " + token));
    }

    private long productoPorCodigo(String token) throws Exception {
        String json = getJson("/api/v1/productos/buscar?codigo=" + CODIGO_BARRAS_COMPARTIDO, token)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("producto").get("id").asLong();
    }
}
