package com.unisen.sgp.config.demo;

import com.unisen.sgp.model.dto.AjusteInventarioDTO;
import com.unisen.sgp.model.dto.CambioEstadoSolicitudDTO;
import com.unisen.sgp.model.dto.CambioEstadoUsuarioDTO;
import com.unisen.sgp.model.dto.ComercialDTO.CondicionesVentaRequest;
import com.unisen.sgp.model.dto.ComercialDTO.PoliticaRequest;
import com.unisen.sgp.model.dto.DetalleSolicitudDTO;
import com.unisen.sgp.model.dto.IncidenciaRecepcionDTO;
import com.unisen.sgp.model.dto.LineaOrdenCompraDTO;
import com.unisen.sgp.model.dto.LineaRecepcionDTO;
import com.unisen.sgp.model.dto.OrdenCompraRequestDTO;
import com.unisen.sgp.model.dto.OrdenCompraResponseDTO;
import com.unisen.sgp.model.dto.ProductoRequestDTO;
import com.unisen.sgp.model.dto.ProveedorRequestDTO;
import com.unisen.sgp.model.dto.RecepcionRequestDTO;
import com.unisen.sgp.model.dto.RegistroRequestDTO;
import com.unisen.sgp.model.dto.SolicitudRequestDTO;
import com.unisen.sgp.model.dto.VentaDTO;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.EstadoSolicitud;
import com.unisen.sgp.model.entity.EstadoUsuario;
import com.unisen.sgp.model.entity.MedioPago;
import com.unisen.sgp.model.entity.OrdenCompra;
import com.unisen.sgp.model.entity.ProductoReferencia;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Solicitud;
import com.unisen.sgp.model.entity.TipoDescuento;
import com.unisen.sgp.model.entity.TipoIncidencia;
import com.unisen.sgp.model.entity.TratamientoIva;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.model.entity.VentaBajoCosto;
import com.unisen.sgp.repository.EmpresaRepository;
import com.unisen.sgp.repository.OrdenCompraRepository;
import com.unisen.sgp.repository.ProductoReferenciaRepository;
import com.unisen.sgp.repository.ProveedorReferenciaRepository;
import com.unisen.sgp.repository.SolicitudRepository;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.repository.VentaRepository;
import com.unisen.sgp.security.CodigosInvitacion;
import com.unisen.sgp.security.UsuarioPrincipal;
import com.unisen.sgp.service.AuthService;
import com.unisen.sgp.service.InventarioService;
import com.unisen.sgp.service.OrdenCompraService;
import com.unisen.sgp.service.ProductoService;
import com.unisen.sgp.service.ProveedorService;
import com.unisen.sgp.service.RecepcionService;
import com.unisen.sgp.service.SolicitudService;
import com.unisen.sgp.service.UsuarioService;
import com.unisen.sgp.service.comercial.ComercialService;
import com.unisen.sgp.service.comercial.DevolucionVentaService;
import com.unisen.sgp.service.comercial.VentaService;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Datos de demostración reproducibles: las cinco empresas ficticias de {@link DatosDemo}, cada una
 * con un administrador comercial y un trabajador. Mercado Verde (MVSA01) tiene el flujo comercial
 * completo: proveedores, productos con precio e IVA de demostración, inventario inicial,
 * solicitudes (pendiente, rechazada y aprobada con su orden recibida en parte), órdenes,
 * recepciones, ventas con descuentos y pagos variados y una devolución parcial. Suministros del
 * Caribe (SCA002) tiene un catálogo propio más pequeño (con un NIT de proveedor, un SKU y un código
 * de barras iguales a los de Mercado Verde, para probar el aislamiento). Distribuciones Nova
 * (DNS003) y Comercializadora Horizonte (CHS004) solo tienen cuentas, con el trabajador PENDIENTE y
 * RECHAZADO; Logística Andina (LAS005) se crea desactivada.
 *
 * <p>Solo existe con el perfil {@code demo}. Todo se hace con los servicios de la aplicación,
 * autenticado como las personas de cada empresa: las reglas, el kárdex y los costos son los
 * mismos que en el uso normal.
 *
 * <p><b>Colisiones</b>: antes de escribir nada se comprueba que ningún código, NIT, username ni
 * correo de la seed lo use un registro ajeno a ella. Si alguno choca, la seed se detiene sin
 * escribir ({@link IllegalStateException} con la lista de conflictos): nunca se reutiliza ni se
 * sobrescribe un registro existente.
 *
 * <p><b>Idempotente</b>: cada empresa se identifica por su código y la marca {@code empresas.demo};
 * usuarios por username, proveedores por NIT, productos por SKU, inventario inicial, recepciones,
 * ventas y devoluciones por claves de idempotencia estables, cada orden base por sus observaciones y
 * cada solicitud por su justificación + solicitante. Volver a ejecutarla solo crea lo que falte: no
 * duplica nada, no cambia contraseñas, estados ni precios, y no toca empresas que no sean suyas.
 *
 * <p><b>Transacciones</b>: por empresa, una para la empresa y sus dos cuentas (con el rechazo del
 * trabajador, si toca) y otra para sus datos de negocio. Si algo falla, lo ya confirmado se
 * conserva y la siguiente ejecución completa lo que falte.
 *
 * <p><b>Contraseña</b>: la de las cuentas demo nuevas se lee de {@code app.demo.password}
 * ({@code DEMO_PASSWORD}), sin valor por defecto: si falta o no cumple la política, no se siembra
 * nada. Nunca se escribe en el código, en la documentación ni en los logs.
 */
@Service
@Profile("demo")
public class DemoSeedService {

    static final String ORDEN_COMPLETA = "Reposición mensual (demo)";
    static final String ORDEN_PENDIENTE = "Pedido pendiente de entrega (demo)";

    private static final Logger log = LoggerFactory.getLogger(DemoSeedService.class);

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final AuthService authService;
    private final ProveedorReferenciaRepository proveedorRepository;
    private final ProveedorService proveedorService;
    private final ProductoReferenciaRepository productoRepository;
    private final ProductoService productoService;
    private final ComercialService comercialService;
    private final InventarioService inventarioService;
    private final SolicitudRepository solicitudRepository;
    private final SolicitudService solicitudService;
    private final OrdenCompraRepository ordenRepository;
    private final OrdenCompraService ordenService;
    private final RecepcionService recepcionService;
    private final VentaRepository ventaRepository;
    private final VentaService ventaService;
    private final DevolucionVentaService devolucionService;
    private final TransactionTemplate transaccion;
    private final String password;

    public DemoSeedService(EmpresaRepository empresaRepository, UsuarioRepository usuarioRepository,
                           UsuarioService usuarioService, AuthService authService,
                           ProveedorReferenciaRepository proveedorRepository, ProveedorService proveedorService,
                           ProductoReferenciaRepository productoRepository, ProductoService productoService,
                           ComercialService comercialService, InventarioService inventarioService,
                           SolicitudRepository solicitudRepository, SolicitudService solicitudService,
                           OrdenCompraRepository ordenRepository, OrdenCompraService ordenService,
                           RecepcionService recepcionService, VentaRepository ventaRepository, VentaService ventaService,
                           DevolucionVentaService devolucionService, TransactionTemplate transaccion,
                           @Value("${app.demo.password:}") String password) {
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.authService = authService;
        this.proveedorRepository = proveedorRepository;
        this.proveedorService = proveedorService;
        this.productoRepository = productoRepository;
        this.productoService = productoService;
        this.comercialService = comercialService;
        this.inventarioService = inventarioService;
        this.solicitudRepository = solicitudRepository;
        this.solicitudService = solicitudService;
        this.ordenRepository = ordenRepository;
        this.ordenService = ordenService;
        this.recepcionService = recepcionService;
        this.ventaRepository = ventaRepository;
        this.ventaService = ventaService;
        this.devolucionService = devolucionService;
        this.transaccion = transaccion;
        this.password = password;
    }

    /**
     * Resultado por empresa: creada, completada (ya existía) o incompleta (la empresa o su
     * administrador ya no están activos: sus datos de negocio no se tocan).
     */
    public enum Resultado {
        CREADA, ACTUALIZADA, INCOMPLETA
    }

    /**
     * @throws IllegalStateException si {@code DEMO_PASSWORD} falta o no cumple la política de
     *                               contraseñas, o si algún dato de la seed choca con un registro
     *                               ajeno (en ambos casos, antes de escribir nada)
     */
    public Map<String, Resultado> sembrar() {
        comprobarPassword(password);
        List<String> conflictos = transaccion.execute(estado -> conflictos());
        if (conflictos != null && !conflictos.isEmpty()) {
            throw new IllegalStateException("Seed demo detenida sin escribir nada: " + String.join("; ", conflictos));
        }
        Map<String, Resultado> resultados = new LinkedHashMap<>();
        for (int i = 0; i < DatosDemo.EMPRESAS.size(); i++) {
            DatosDemo.Empresa datos = DatosDemo.EMPRESAS.get(i);
            // 1) Empresa y cuentas (tablas globales), en su transacción.
            Alta alta = transaccion.execute(estado -> alta(datos));
            if (alta.trabajadorId() == null) {
                resultados.put(datos.codigo(), Resultado.INCOMPLETA);
                continue;
            }
            if (datos.negocio() == null) {
                resultados.put(datos.codigo(), alta.nueva() ? Resultado.CREADA : Resultado.ACTUALIZADA);
                continue;
            }
            // 2) Datos de negocio: la transacción se abre YA dentro de la empresa (Hibernate fija el
            //    tenant de la sesión al abrirla), autenticada como sus personas.
            Boolean completa = TenantContextHolder.conEmpresa(alta.empresaId(), () -> transaccion.execute(estado -> {
                Empresa empresa = empresaRepository.findById(alta.empresaId()).orElseThrow();
                Usuario admin = usuarioRepository.findById(alta.adminId()).orElseThrow();
                Usuario trabajador = usuarioRepository.findById(alta.trabajadorId()).orElseThrow();
                if (!empresa.isActiva() || !activa(admin)) {
                    // Nunca se actúa en nombre de una cuenta sin acceso ni se reactiva nada.
                    log.warn("Seed demo: {} {}; se omiten sus datos de negocio.", datos.codigo(),
                            empresa.isActiva() ? "tiene a su administrador " + admin.getUsername() + " sin acceso"
                                    : "está desactivada");
                    return false;
                }
                DatosDemo.Negocio negocio = datos.negocio();
                Map<String, ProductoReferencia> productos = como(admin, empresa, () -> {
                    Map<String, ProductoReferencia> catalogo = catalogo(datos.codigo(), negocio);
                    compras(catalogo);
                    return catalogo;
                });
                for (DatosDemo.Solicitud s : negocio.solicitudes()) {
                    solicitud(s, datos.codigo(), empresa, admin, trabajador, productos);
                }
                ventas(datos.codigo(), negocio, admin, trabajador, empresa, productos);
                return true;
            }));
            resultados.put(datos.codigo(), !Boolean.TRUE.equals(completa) ? Resultado.INCOMPLETA
                    : alta.nueva() ? Resultado.CREADA : Resultado.ACTUALIZADA);
        }
        log.info("Seed de demostración terminada: {}", resultados);
        return resultados;
    }

    // ---------------------------------------------------------------- colisiones y altas

    /**
     * Códigos, NIT, usernames y correos de la seed que ya usa un registro ajeno. Una empresa con
     * el código de la seed solo es «suya» si está marcada como demo y tiene su NIT; una cuenta,
     * si pertenece a esa empresa con el rol y el correo previstos.
     */
    private List<String> conflictos() {
        List<String> conflictos = new ArrayList<>();
        for (DatosDemo.Empresa datos : DatosDemo.EMPRESAS) {
            Optional<Empresa> existente = empresaRepository.findByCodigo(datos.codigo());
            if (existente.isPresent() && !(existente.get().isDemo() && existente.get().getNit().equals(datos.nit()))) {
                conflictos.add("el código " + datos.codigo() + " lo usa una empresa que no es de esta seed");
                continue;
            }
            if (existente.isEmpty() && empresaRepository.existsByNit(datos.nit())) {
                conflictos.add("el NIT de " + datos.codigo() + " lo usa otra empresa");
            }
            Long empresaId = existente.map(Empresa::getId).orElse(null);
            conflictoDeCuenta(datos.administrador(), Rol.ADMINISTRADOR_COMERCIAL, datos.codigo(), empresaId)
                    .ifPresent(conflictos::add);
            conflictoDeCuenta(datos.trabajador(), Rol.TRABAJADOR, datos.codigo(), empresaId).ifPresent(conflictos::add);
        }
        return conflictos;
    }

    private Optional<String> conflictoDeCuenta(DatosDemo.Persona p, Rol rol, String codigo, Long empresaId) {
        String correo = Usuario.normalizarEmail(DatosDemo.correo(p, codigo));
        Optional<Usuario> existente = usuarioRepository.findByUsername(Usuario.normalizarUsername(p.usuario()));
        if (existente.isPresent()) {
            Usuario u = existente.get();
            boolean suya = empresaId != null && u.getEmpresa() != null && empresaId.equals(u.getEmpresa().getId())
                    && u.getRol() == rol && correo.equals(u.getEmail());
            return suya ? Optional.empty()
                    : Optional.of("el username " + p.usuario() + " lo usa una cuenta que no es de esta seed");
        }
        return usuarioRepository.existsByEmail(correo)
                ? Optional.of("el correo de " + p.usuario() + " lo usa otra cuenta")
                : Optional.empty();
    }

    /** @param trabajadorId null si no se pudo registrar al trabajador (empresa o administrador sin acceso) */
    private record Alta(Long empresaId, Long adminId, Long trabajadorId, boolean nueva) {
    }

    /**
     * Empresa y cuentas que falten (ya comprobado que no chocan con nada ajeno). Las cuentas que ya
     * existen no se tocan. Un trabajador PENDIENTE o RECHAZADO se registra con el código de la
     * empresa, como lo haría la persona, y el rechazo lo hace el administrador en esta misma
     * transacción: una cuenta a medias nunca queda confirmada.
     */
    private Alta alta(DatosDemo.Empresa datos) {
        Optional<Empresa> existente = empresaRepository.findByCodigo(datos.codigo());
        Empresa empresa = existente.orElseGet(() -> {
            Empresa nueva = new Empresa(datos.nombre(), datos.nit(), datos.codigo(),
                    CodigosInvitacion.generarUnico(empresaRepository::existsByCodigoEmpresa));
            nueva.marcarDemo();
            return empresaRepository.saveAndFlush(nueva);
        });
        Usuario admin = usuarioRepository.findByUsername(datos.administrador().usuario())
                .orElseGet(() -> crear(datos.administrador(), Rol.ADMINISTRADOR_COMERCIAL, empresa, datos.codigo()));
        DatosDemo.Persona t = datos.trabajador();
        Optional<Usuario> trabajadorExistente = usuarioRepository.findByUsername(t.usuario());
        Long trabajadorId;
        if (trabajadorExistente.isPresent()) {
            trabajadorId = trabajadorExistente.get().getId();
        } else if (datos.estadoTrabajador() == EstadoUsuario.ACTIVO) {
            trabajadorId = crear(t, Rol.TRABAJADOR, empresa, datos.codigo()).getId();
        } else {
            if (!empresa.isActiva() || !activa(admin)) {
                // Nunca se registra en una empresa desactivada ni se rechaza en nombre de una cuenta sin acceso.
                log.warn("Seed demo: {} no admite registros ahora (empresa o administrador sin acceso); "
                        + "se omite la cuenta {}.", datos.codigo(), t.usuario());
                return new Alta(empresa.getId(), admin.getId(), null, existente.isEmpty());
            }
            trabajadorId = authService.registrar(new RegistroRequestDTO(t.usuario(), DatosDemo.correo(t, datos.codigo()),
                    password, null, empresa.getCodigoEmpresa(), t.nombres(), t.apellidos())).id();
            if (datos.estadoTrabajador() == EstadoUsuario.RECHAZADO) {
                como(admin, empresa, () -> usuarioService.cambiarEstadoTrabajador(trabajadorId,
                        new CambioEstadoUsuarioDTO(EstadoUsuario.RECHAZADO)));
            }
        }
        if (existente.isEmpty() && !datos.activa()) {
            // Solo al crearla: mismo cambio que hace EmpresaService.cambiarEstado (la seed no tiene SUPER_ADMIN).
            empresa.setActiva(false);
            empresaRepository.saveAndFlush(empresa);
        }
        return new Alta(empresa.getId(), admin.getId(), trabajadorId, existente.isEmpty());
    }

    private Usuario crear(DatosDemo.Persona p, Rol rol, Empresa empresa, String codigo) {
        return usuarioService.crearUsuario(p.usuario(), DatosDemo.correo(p, codigo), p.nombres(), p.apellidos(), password,
                rol, empresa);
    }

    /** La cuenta puede usar la aplicación (ACTIVA): la seed solo actúa en nombre de cuentas así. */
    static boolean activa(Usuario usuario) {
        return usuario.getEstado().permiteAcceso();
    }

    /** Órdenes con exactamente esas observaciones (en la empresa del contexto). */
    static Specification<OrdenCompra> conObservaciones(String observaciones) {
        return (root, query, cb) -> cb.equal(root.get("observaciones"), observaciones);
    }

    /** Falla antes de crear nada: sin contraseña no hay cuentas demo. */
    static void comprobarPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("La seed de demostración necesita la contraseña de las cuentas demo: "
                    + "define la variable de entorno DEMO_PASSWORD (no tiene valor por defecto).");
        }
        if (password.length() < UsuarioService.PASSWORD_MIN_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > UsuarioService.PASSWORD_MAX_BYTES) {
            throw new IllegalStateException("DEMO_PASSWORD debe tener al menos " + UsuarioService.PASSWORD_MIN_LENGTH
                    + " caracteres y no superar " + UsuarioService.PASSWORD_MAX_BYTES + " bytes.");
        }
    }

    // ---------------------------------------------------------------- catálogo y compras

    /**
     * Política, proveedores y productos que falten; devuelve los productos activos por SKU. Un
     * producto que ya existe (de una ejecución anterior o creado a mano) conserva su precio y su
     * stock: el precio y el inventario inicial se aplican solo al crearlo, en la misma transacción.
     */
    private Map<String, ProductoReferencia> catalogo(String codigo, DatosDemo.Negocio negocio) {
        // Las búsquedas por NIT y SKU solo son correctas dentro de una empresa (ver ProveedorReferenciaRepository).
        TenantContextHolder.requerirEmpresa();
        if (comercialService.consultarPolitica().porDefecto()) {
            // Valores SOLO de demostración (no son una política recomendada): trabajador 10 %, administrador 30 %.
            comercialService.actualizarPolitica(new PoliticaRequest(new BigDecimal("10"), new BigDecimal("30"),
                    VentaBajoCosto.PERMITIR, new BigDecimal("30")));
        }
        // Un proveedor demo dado de baja después se respeta (no se recrea: su NIT sigue ocupado);
        // sus productos que falten no se crean (null).
        List<Long> proveedores = new ArrayList<>();
        for (DatosDemo.Proveedor p : negocio.proveedores()) {
            proveedores.add(proveedorRepository.findByNit(p.nit())
                    .map(prov -> prov.isActivo() ? prov.getId() : null)
                    .orElseGet(() -> proveedorService.crear(new ProveedorRequestDTO(p.nit(), p.razonSocial(), p.email(),
                            p.telefono(), null)).id()));
        }
        Map<String, ProductoReferencia> productos = new LinkedHashMap<>();
        for (int i = 0; i < negocio.productos().size(); i++) {
            DatosDemo.Producto p = negocio.productos().get(i);
            Long proveedor = proveedores.get(p.proveedor());
            Optional<ProductoReferencia> previo = productoRepository.findBySku(p.sku());
            if (previo.isEmpty() && proveedor != null) {
                long id = productoService.crear(new ProductoRequestDTO(p.sku(), p.codigoBarras(), p.nombre(),
                        "Producto ficticio de demostración.", new BigDecimal(p.costo()), p.stockMinimo(), proveedor)).id();
                comercialService.cambiarCondiciones(id, new CondicionesVentaRequest(new BigDecimal(p.precioVenta()),
                        TratamientoIva.valueOf(p.tratamiento()), new BigDecimal(p.tarifa()),
                        "Precio inicial de demostración (IVA ficticio)"));
                if (p.stock() > 0) {
                    inventarioService.ajustar(new AjusteInventarioDTO(id, p.stock(), "Inventario inicial de demostración",
                            new BigDecimal(p.costo()), "demo-" + codigo + "-ini-" + (i + 1)));
                }
                previo = productoRepository.findById(id);
            }
            previo.filter(ProductoReferencia::isActivo).ifPresent(prod -> productos.put(p.sku(), prod));
        }
        return productos;
    }

    /**
     * Dos órdenes manuales: una recibida por completo (los dos primeros productos de su primer
     * proveedor) y otra emitida pendiente de entrega (el último producto). Cada una se identifica
     * por sus observaciones y solo se crea, con su recepción, si no existe.
     */
    private void compras(Map<String, ProductoReferencia> productos) {
        List<ProductoReferencia> lista = new ArrayList<>(productos.values());
        if (lista.size() < 3) {
            log.warn("Seed demo: faltan productos activos; se omiten las órdenes base.");
            return;
        }
        if (!existeOrden(ORDEN_COMPLETA)) {
            List<ProductoReferencia> mismoProveedor = lista.stream()
                    .filter(p -> p.getProveedorId().equals(lista.get(0).getProveedorId()))
                    .limit(2).toList();
            OrdenCompraResponseDTO completa = emitir(mismoProveedor, 10, ORDEN_COMPLETA);
            recepcionService.registrar(completa.id(), new RecepcionRequestDTO(clave(completa, "rec"),
                    "Entrega completa (demo)", completa.detalles().stream()
                            .map(l -> new LineaRecepcionDTO(l.id(), l.cantidad())).toList(), List.of()));
        }
        if (!existeOrden(ORDEN_PENDIENTE)) {
            emitir(List.of(lista.get(lista.size() - 1)), 24, ORDEN_PENDIENTE);
        }
    }

    /** Clave de idempotencia estable de una recepción de la seed (por empresa y orden). */
    private static String clave(OrdenCompraResponseDTO orden, String tipo) {
        return "demo-" + TenantContextHolder.requerirEmpresa() + "-" + tipo + "-" + orden.id();
    }

    private boolean existeOrden(String observaciones) {
        return !ordenRepository.findAll(conObservaciones(observaciones)).isEmpty();
    }

    private OrdenCompraResponseDTO emitir(List<ProductoReferencia> productos, int cantidad, String observaciones) {
        List<LineaOrdenCompraDTO> lineas = productos.stream()
                .map(p -> new LineaOrdenCompraDTO(p.getId(), cantidad,
                        p.getPrecio().multiply(new BigDecimal("1.05")).setScale(0, RoundingMode.HALF_UP)))
                .toList();
        OrdenCompraResponseDTO borrador = ordenService.crear(new OrdenCompraRequestDTO(productos.get(0).getProveedorId(),
                observaciones, lineas));
        return ordenService.emitir(borrador.id());
    }

    // ---------------------------------------------------------------- solicitudes

    /**
     * La crea el trabajador; el administrador la revisa y, si corresponde, genera su orden, que
     * recibe el trabajador en parte (la mitad de cada línea y un faltante informado). Si ya existe
     * una solicitud con esa justificación de ese trabajador, no se hace nada; si el trabajador no
     * está ACTIVO, se omite con un aviso.
     */
    private void solicitud(DatosDemo.Solicitud s, String codigo, Empresa empresa, Usuario admin, Usuario solicitante,
                           Map<String, ProductoReferencia> productos) {
        Specification<Solicitud> clave = (root, query, cb) -> cb.and(
                cb.equal(root.get("justificacion"), s.justificacion()),
                cb.equal(root.get("solicitante").get("id"), solicitante.getId()));
        if (!solicitudRepository.findAll(clave).isEmpty()) {
            return;
        }
        if (!activa(solicitante) || !s.lineas().stream().allMatch(l -> productos.containsKey(l.sku()))) {
            log.warn("Seed demo: se omite en {} la solicitud «{}» ({} sin acceso o productos no disponibles).", codigo,
                    s.justificacion(), solicitante.getUsername());
            return;
        }
        List<DetalleSolicitudDTO> lineas = s.lineas().stream()
                .map(l -> new DetalleSolicitudDTO(productos.get(l.sku()).getId(), l.cantidad()))
                .toList();
        Long id = como(solicitante, empresa, () -> solicitudService.crear(new SolicitudRequestDTO(s.justificacion(), lineas)).id());
        if (s.estado() == EstadoSolicitud.PENDIENTE) {
            return;
        }
        como(admin, empresa, () -> solicitudService.cambiarEstado(id, new CambioEstadoSolicitudDTO(s.estado(), s.comentario())));
        if (s.proceso() != DatosDemo.Proceso.ORDEN_RECIBIDA_EN_PARTE) {
            return;
        }
        OrdenCompraResponseDTO orden = como(admin, empresa,
                () -> ordenService.emitir(ordenService.crearDesdeSolicitud(id).get(0).id()));
        OrdenCompraResponseDTO.Linea primera = orden.detalles().get(0);
        como(solicitante, empresa, () -> recepcionService.registrar(orden.id(), new RecepcionRequestDTO(
                clave(orden, "rec"), "Entrega incompleta: el proveedor enviará el resto (demo)",
                orden.detalles().stream().map(l -> new LineaRecepcionDTO(l.id(), l.cantidad() / 2)).toList(),
                List.of(new IncidenciaRecepcionDTO(TipoIncidencia.FALTANTE, primera.id(), 2,
                        "Faltaron dos unidades en la remisión (demo)")))));
    }

    // ---------------------------------------------------------------- ventas

    /**
     * Ventas con claves estables. Siempre: una en efectivo con cambio y otra con tarjeta y un
     * descuento de línea. En el flujo completo, además: descuento general del administrador, una
     * bajo el costo, un pago mixto y una devolución parcial de la primera.
     */
    private void ventas(String codigo, DatosDemo.Negocio negocio, Usuario admin, Usuario trabajador, Empresa empresa,
                        Map<String, ProductoReferencia> productos) {
        List<ProductoReferencia> conStock = como(admin, empresa, () -> productos.values().stream()
                .filter(p -> p.getPrecioVenta() != null && ventaService.porCodigo(p.getSku()).existencias() > 3).toList());
        if (conStock.size() < 3) {
            log.warn("Seed demo: {} no tiene productos suficientes con existencias; se omiten sus ventas.", codigo);
            return;
        }
        String c = "demo-" + codigo + "-v-";
        Long primera = vender(trabajador, empresa, c + 1, List.of(linea(conStock.get(0), 2, null), linea(conStock.get(1), 1, null)),
                null, MedioPago.EFECTIVO, true);
        vender(trabajador, empresa, c + 2, List.of(linea(conStock.get(2), 1, new VentaDTO.DescuentoRequest(TipoDescuento.PORCENTAJE,
                new BigDecimal("5")))), null, MedioPago.TARJETA_DEBITO, false);
        if (!negocio.completo()) {
            return;
        }
        vender(admin, empresa, c + 3, List.of(linea(conStock.get(0), 1, new VentaDTO.DescuentoRequest(TipoDescuento.PORCENTAJE,
                        new BigDecimal("20"))), linea(conStock.get(1), 2, null)),
                new VentaDTO.DescuentoRequest(TipoDescuento.PORCENTAJE, new BigDecimal("2")), MedioPago.TRANSFERENCIA, false);
        // El primer producto cuyo precio de venta (de los datos) es menor que su costo.
        negocio.productos().stream()
                .filter(p -> new BigDecimal(p.precioVenta()).compareTo(new BigDecimal(p.costo())) < 0)
                .map(p -> conStock.stream().filter(s -> s.getSku().equals(p.sku())).findFirst())
                .flatMap(Optional::stream).findFirst()
                .ifPresent(bajoCosto -> vender(trabajador, empresa, c + 4, List.of(linea(bajoCosto, 1, null)), null,
                        MedioPago.EFECTIVO, false));
        vender(trabajador, empresa, c + 5, List.of(linea(conStock.get(2), 2, null), linea(conStock.get(0), 1, null)), null,
                null, false);

        if (primera != null) {
            como(admin, empresa, () -> {
                var venta = ventaService.obtener(primera);
                if (venta.devoluciones().isEmpty()) {
                    devolucionService.devolver(primera, new VentaDTO.DevolucionRequest("demo-" + codigo + "-dev-1",
                            "Cliente devolvió una unidad sin abrir (demo)",
                            List.of(new VentaDTO.LineaDevolucionRequest(venta.lineas().get(0).id(), 1, true))));
                }
                return null;
            });
        }
    }

    private static VentaDTO.LineaRequest linea(ProductoReferencia p, int cantidad, VentaDTO.DescuentoRequest descuento) {
        return new VentaDTO.LineaRequest(p.getId(), cantidad, descuento);
    }

    /**
     * Vende con la clave dada si aún no existe (con un pago por el total calculado por el servidor;
     * {@code medio} null = pago mixto efectivo + tarjeta). Devuelve el id de la venta.
     */
    private Long vender(Usuario vendedor, Empresa empresa, String clave, List<VentaDTO.LineaRequest> lineas,
                        VentaDTO.DescuentoRequest general, MedioPago medio, boolean conCambio) {
        return como(vendedor, empresa, () -> {
            Optional<Long> existente = ventaRepository.findByClaveIdempotencia(clave).map(v -> v.getId());
            if (existente.isPresent()) {
                return existente.get();
            }
            if (!activa(vendedor)) {
                log.warn("Seed demo: {} no está activo; se omite la venta {}.", vendedor.getUsername(), clave);
                return null;
            }
            VentaDTO.Cotizacion cotizacion = ventaService.cotizar(new VentaDTO.CotizacionRequest(lineas, general, null));
            if (!cotizacion.existenciasSuficientes() || cotizacion.requiereAutorizacion()) {
                log.info("Seed demo: venta {} omitida (sin existencias o requiere autorización).", clave);
                return null;
            }
            BigDecimal total = cotizacion.total();
            List<VentaDTO.PagoRequest> pagos;
            if (medio == null) {
                BigDecimal tarjeta = total.divide(BigDecimal.TWO, 0, RoundingMode.DOWN);
                pagos = List.of(new VentaDTO.PagoRequest(MedioPago.TARJETA_CREDITO, tarjeta, null, "DEMO-" + clave.hashCode()),
                        new VentaDTO.PagoRequest(MedioPago.EFECTIVO, total.subtract(tarjeta), null, null));
            } else if (medio == MedioPago.EFECTIVO && conCambio) {
                BigDecimal recibido = total.divide(new BigDecimal("10000"), 0, RoundingMode.UP)
                        .multiply(new BigDecimal("10000"));
                pagos = List.of(new VentaDTO.PagoRequest(medio, total, recibido, null));
            } else {
                pagos = List.of(new VentaDTO.PagoRequest(medio, total, null, null));
            }
            return ventaService.confirmar(new VentaDTO.VentaRequest(clave, lineas, general, pagos, null,
                    "Cliente de mostrador (demo)", null)).valor().id();
        });
    }

    /** Ejecuta {@code accion} autenticado como {@code usuario} dentro de su empresa, y restaura el contexto. */
    static <T> T como(Usuario usuario, Empresa empresa, Supplier<T> accion) {
        SecurityContext anterior = SecurityContextHolder.getContext();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        UsuarioPrincipal principal = UsuarioPrincipal.from(usuario);
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                principal.getAuthorities()));
        SecurityContextHolder.setContext(contexto);
        try {
            return TenantContextHolder.conEmpresa(empresa.getId(), accion);
        } finally {
            SecurityContextHolder.setContext(anterior);
        }
    }
}
