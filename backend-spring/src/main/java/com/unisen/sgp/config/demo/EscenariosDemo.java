package com.unisen.sgp.config.demo;

import static com.unisen.sgp.config.demo.DemoSeedService.activa;
import static com.unisen.sgp.config.demo.DemoSeedService.como;
import static com.unisen.sgp.config.demo.DemoSeedService.conObservaciones;
import static com.unisen.sgp.config.demo.DemoSeedService.correo;

import com.unisen.sgp.model.dto.AjusteInventarioDTO;
import com.unisen.sgp.model.dto.CambioEstadoSolicitudDTO;
import com.unisen.sgp.model.dto.CambioEstadoUsuarioDTO;
import com.unisen.sgp.model.dto.CancelacionOrdenDTO;
import com.unisen.sgp.model.dto.DetalleSolicitudDTO;
import com.unisen.sgp.model.dto.IncidenciaRecepcionDTO;
import com.unisen.sgp.model.dto.InvitacionResponseDTO;
import com.unisen.sgp.model.dto.LineaOrdenCompraDTO;
import com.unisen.sgp.model.dto.LineaRecepcionDTO;
import com.unisen.sgp.model.dto.OrdenCompraRequestDTO;
import com.unisen.sgp.model.dto.OrdenCompraResponseDTO;
import com.unisen.sgp.model.dto.ProductoRequestDTO;
import com.unisen.sgp.model.dto.ProgramacionEntregaRequestDTO;
import com.unisen.sgp.model.dto.ProveedorRequestDTO;
import com.unisen.sgp.model.dto.RecepcionRequestDTO;
import com.unisen.sgp.model.dto.RegistroRequestDTO;
import com.unisen.sgp.model.dto.SolicitudRequestDTO;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.EstadoSolicitud;
import com.unisen.sgp.model.entity.EstadoUsuario;
import com.unisen.sgp.model.entity.ProductoReferencia;
import com.unisen.sgp.model.entity.ProveedorReferencia;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Solicitud;
import com.unisen.sgp.model.entity.TipoIncidencia;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.CodigoInvitacionRepository;
import com.unisen.sgp.repository.OrdenCompraRepository;
import com.unisen.sgp.repository.ProductoReferenciaRepository;
import com.unisen.sgp.repository.ProveedorReferenciaRepository;
import com.unisen.sgp.repository.SolicitudRepository;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.service.AuthService;
import com.unisen.sgp.service.CalendarioNegocio;
import com.unisen.sgp.service.EntregaService;
import com.unisen.sgp.service.InventarioService;
import com.unisen.sgp.service.InvitacionService;
import com.unisen.sgp.service.OrdenCompraService;
import com.unisen.sgp.service.ProductoService;
import com.unisen.sgp.service.ProveedorService;
import com.unisen.sgp.service.RecepcionService;
import com.unisen.sgp.service.SolicitudService;
import com.unisen.sgp.service.UsuarioService;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * Escenarios completos de prueba de las empresas demo que los declaran (DEMO01 y DEMO02):
 * usuarios PENDIENTE, RECHAZADO e INACTIVO; invitaciones vigente, usada, revocada y de una hora;
 * proveedor y producto dados de baja; productos en el stock mínimo y por debajo; solicitudes
 * pendientes, rechazada, aprobada sin orden y aprobadas con orden; órdenes en borrador, cancelada,
 * recibida con excedente y recibida en parte con faltante y daño, con entrega programada.
 *
 * <p>Como el resto de la seed, todo pasa por los servicios reales, autenticado como las personas
 * de la empresa. Se ejecuta dentro de la transacción y del contexto de la empresa que abre
 * {@link DemoSeedService}.
 *
 * <p><b>Idempotencia</b>: cada escenario tiene su clave natural (username, NIT, SKU, justificación
 * de la solicitud + solicitante, observaciones de una orden que ya no admite cambios) y solo se
 * crea, con todos sus pasos, si esa clave no existe. Un escenario que ya existe no se modifica
 * nunca, aunque alguien lo haya cambiado después (p. ej. reactivado al usuario inactivo o aprobado
 * una solicitud pendiente).
 *
 * <p><b>Cuentas</b>: nunca se actúa en nombre de una cuenta que no esté ACTIVA ni se reutiliza un
 * username o correo de otra empresa. El escenario que dependa de una de ellas se omite con un aviso
 * en el log (sin reactivar ni modificar nada) y se creará en una ejecución posterior si la causa
 * desaparece.
 */
@Service
@Profile("demo")
public class EscenariosDemo {

    /**
     * Observaciones de las órdenes manuales. Las de la cancelada y la recibida identifican su
     * escenario: al terminar de crearse ya no admiten cambios (solo se edita un borrador). El
     * borrador sí puede editarse, así que no se identifica por las suyas: se crea solo junto con
     * la cancelada (ver {@link #ordenesManuales}).
     */
    static final String ORDEN_BORRADOR = "Borrador sin emitir (demo escenarios)";
    static final String ORDEN_CANCELADA = "Pedido cancelado por cambio de proveedor (demo escenarios)";
    static final String ORDEN_EXCEDENTE = "Entrega completa con excedente (demo escenarios)";

    private static final Logger log = LoggerFactory.getLogger(EscenariosDemo.class);

    /** Qué se hace con una solicitud aprobada. */
    private enum Proceso {
        NINGUNO, ORDEN_EN_BORRADOR, ORDEN_RECIBIDA_EN_PARTE
    }

    private record Linea(String sku, int cantidad) {
    }

    /**
     * @param solicitante índice del trabajador que la pide
     * @param estado      estado final tras la revisión del administrador
     */
    private record SolicitudDemo(int solicitante, String justificacion, List<Linea> lineas, EstadoSolicitud estado,
                                 String comentario, Proceso proceso) {
    }

    private static final List<SolicitudDemo> SOLICITUDES = List.of(
            new SolicitudDemo(0, "Reposición del producto compartido A para la semana (demo).",
                    List.of(new Linea("ESC-COM-01", 5)), EstadoSolicitud.PENDIENTE, null, Proceso.NINGUNO),
            new SolicitudDemo(1, "Producto bajo el stock mínimo: hay que reponerlo pronto (demo).",
                    List.of(new Linea("ESC-MIN-BA", 10), new Linea("ESC-COM-02", 2)), EstadoSolicitud.PENDIENTE, null,
                    Proceso.NINGUNO),
            new SolicitudDemo(0, "Compra grande del producto compartido B para una promoción (demo).",
                    List.of(new Linea("ESC-COM-02", 50)), EstadoSolicitud.RECHAZADA,
                    "Cantidad excesiva para la rotación actual (demo).", Proceso.NINGUNO),
            new SolicitudDemo(1, "Reponer el producto que está justo en el mínimo (demo).",
                    List.of(new Linea("ESC-MIN-IG", 6)), EstadoSolicitud.APROBADA,
                    "Aprobada; la orden se generará la próxima semana (demo).", Proceso.NINGUNO),
            new SolicitudDemo(0, "Pedido del producto compartido B para el mes (demo).",
                    List.of(new Linea("ESC-COM-02", 8)), EstadoSolicitud.APROBADA, null, Proceso.ORDEN_EN_BORRADOR),
            new SolicitudDemo(1, "Pedido urgente del producto compartido A (demo).",
                    List.of(new Linea("ESC-COM-01", 10)), EstadoSolicitud.APROBADA, "Urgente: generar la orden hoy (demo).",
                    Proceso.ORDEN_RECIBIDA_EN_PARTE));

    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final AuthService authService;
    private final InvitacionService invitacionService;
    private final CodigoInvitacionRepository invitacionRepository;
    private final ProveedorReferenciaRepository proveedorRepository;
    private final ProveedorService proveedorService;
    private final ProductoReferenciaRepository productoRepository;
    private final ProductoService productoService;
    private final InventarioService inventarioService;
    private final SolicitudRepository solicitudRepository;
    private final SolicitudService solicitudService;
    private final OrdenCompraRepository ordenRepository;
    private final OrdenCompraService ordenService;
    private final EntregaService entregaService;
    private final RecepcionService recepcionService;
    private final CalendarioNegocio calendario;

    public EscenariosDemo(UsuarioRepository usuarioRepository, UsuarioService usuarioService, AuthService authService,
                          InvitacionService invitacionService, CodigoInvitacionRepository invitacionRepository,
                          ProveedorReferenciaRepository proveedorRepository, ProveedorService proveedorService,
                          ProductoReferenciaRepository productoRepository, ProductoService productoService,
                          InventarioService inventarioService, SolicitudRepository solicitudRepository,
                          SolicitudService solicitudService, OrdenCompraRepository ordenRepository,
                          OrdenCompraService ordenService, EntregaService entregaService,
                          RecepcionService recepcionService, CalendarioNegocio calendario) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.authService = authService;
        this.invitacionService = invitacionService;
        this.invitacionRepository = invitacionRepository;
        this.proveedorRepository = proveedorRepository;
        this.proveedorService = proveedorService;
        this.productoRepository = productoRepository;
        this.productoService = productoService;
        this.inventarioService = inventarioService;
        this.solicitudRepository = solicitudRepository;
        this.solicitudService = solicitudService;
        this.ordenRepository = ordenRepository;
        this.ordenService = ordenService;
        this.entregaService = entregaService;
        this.recepcionService = recepcionService;
        this.calendario = calendario;
    }

    /**
     * Crea los escenarios que aún no existen. Debe llamarse dentro del contexto y la transacción de
     * la empresa, con su administrador ACTIVO (lo comprueba {@link DemoSeedService}); los dos
     * primeros trabajadores se usan como solicitantes y receptores si están ACTIVOS.
     *
     * @throws com.unisen.sgp.exception.EmpresaNoSeleccionadaException sin contexto de empresa (antes de
     *                                                                 consultar nada)
     */
    void sembrar(DatosDemo.Empresa datos, Empresa empresa, Usuario admin, List<Usuario> trabajadores, String password) {
        // Las búsquedas por NIT y SKU solo son correctas dentro de una empresa (ver ProveedorReferenciaRepository).
        TenantContextHolder.requerirEmpresa();
        DatosDemo.Escenarios e = datos.escenarios();
        String codigo = datos.codigo();
        como(admin, empresa, () -> {
            usuarios(e, codigo, empresa, password);
            invitaciones(e, codigo, empresa, admin, password);
            Map<String, ProductoReferencia> productos = catalogo(e, codigo);
            if (productos.isEmpty()) {
                return null;
            }
            for (SolicitudDemo s : SOLICITUDES) {
                solicitud(s, codigo, empresa, admin, trabajadores, productos);
            }
            ordenesManuales(codigo, empresa, trabajadores, productos);
            return null;
        });
    }

    // ---------------------------------------------------------------- usuarios e invitaciones

    /** Pendiente y rechazado se registran con el código de la empresa, como lo haría la persona. */
    private void usuarios(DatosDemo.Escenarios e, String codigo, Empresa empresa, String password) {
        if (cuenta(e.pendiente(), codigo, empresa, "usuario pendiente") == Cuenta.LIBRE) {
            registrar(e.pendiente(), codigo, password, empresa.getCodigoEmpresa(), null);
        }
        if (cuenta(e.rechazado(), codigo, empresa, "usuario rechazado") == Cuenta.LIBRE) {
            Long id = registrar(e.rechazado(), codigo, password, empresa.getCodigoEmpresa(), null);
            usuarioService.cambiarEstadoTrabajador(id, new CambioEstadoUsuarioDTO(EstadoUsuario.RECHAZADO));
        }
        if (cuenta(e.inactivo(), codigo, empresa, "usuario inactivo") == Cuenta.LIBRE) {
            DatosDemo.Persona p = e.inactivo();
            Usuario u = usuarioService.crearUsuario(p.usuario(), correo(p, codigo), p.nombres(), p.apellidos(), password,
                    Rol.TRABAJADOR, empresa);
            usuarioService.cambiarEstadoTrabajador(u.getId(), new CambioEstadoUsuarioDTO(EstadoUsuario.INACTIVO));
        }
    }

    /**
     * Vigente (30 días), de una hora (caduca sola), revocada y usada por {@code invitado}. No tienen
     * clave estable (el código es aleatorio): se crean juntas solo si el invitado aún no existe.
     */
    private void invitaciones(DatosDemo.Escenarios e, String codigo, Empresa empresa, Usuario admin, String password) {
        if (cuenta(e.invitado(), codigo, empresa, "invitaciones (vigente, de 1 h, revocada y usada)") != Cuenta.LIBRE) {
            return;
        }
        invitacionService.generar(admin.getId(), 720);
        invitacionService.generar(admin.getId(), 1);
        InvitacionResponseDTO revocada = invitacionService.generar(admin.getId(), 72);
        Long idRevocada = invitacionRepository.findByCodigo(revocada.codigo()).orElseThrow().getId();
        invitacionService.revocar(idRevocada, admin.getId());
        InvitacionResponseDTO usada = invitacionService.generar(admin.getId(), 72);
        registrar(e.invitado(), codigo, password, null, usada.codigo());
    }

    /** Alta pública real (AuthService); devuelve el id del usuario. */
    private Long registrar(DatosDemo.Persona p, String codigo, String password, String codigoEmpresa,
                           String codigoInvitacion) {
        return authService.registrar(new RegistroRequestDTO(p.usuario(), correo(p, codigo), password, codigoInvitacion,
                codigoEmpresa, p.nombres(), p.apellidos())).id();
    }

    /** Situación de la cuenta de un escenario. */
    private enum Cuenta {
        /** Ni el username ni el correo existen: se puede crear. */
        LIBRE,
        /** Ya es un trabajador de esta empresa: el escenario se creó antes (no se toca). */
        CREADA,
        /** Username o correo en uso por otra cuenta (otra empresa u otro rol): el escenario se omite. */
        OCUPADA
    }

    /** Nunca modifica la cuenta existente; si está ocupada, avisa de lo que se omite. */
    private Cuenta cuenta(DatosDemo.Persona p, String codigo, Empresa empresa, String escenario) {
        Optional<Usuario> existente = usuarioRepository.findByUsername(Usuario.normalizarUsername(p.usuario()));
        if (existente.isPresent()) {
            Usuario u = existente.get();
            if (u.getRol() == Rol.TRABAJADOR && u.getEmpresa() != null && empresa.getId().equals(u.getEmpresa().getId())) {
                return Cuenta.CREADA;
            }
        } else if (!usuarioRepository.existsByEmail(Usuario.normalizarEmail(correo(p, codigo)))) {
            return Cuenta.LIBRE;
        }
        log.warn("Seed demo: el username o el correo de {} ya los usa otra cuenta; se omite en {} el escenario: {}.",
                p.usuario(), codigo, escenario);
        return Cuenta.OCUPADA;
    }

    // ---------------------------------------------------------------- catálogo

    /**
     * Proveedor y productos compartidos, más un proveedor y un producto dados de baja. Devuelve
     * los productos activos por SKU; vacío si el proveedor de los escenarios fue dado de baja
     * (entonces no se crea nada que dependa de él).
     */
    private Map<String, ProductoReferencia> catalogo(DatosDemo.Escenarios e, String codigo) {
        Optional<ProveedorReferencia> existente = proveedorRepository.findByNit(e.proveedor().nit());
        if (existente.isPresent() && !existente.get().isActivo()) {
            log.warn("Seed demo: el proveedor de escenarios de {} fue dado de baja; se omiten los escenarios de compras.",
                    codigo);
            return Map.of();
        }
        long proveedorId = existente.map(ProveedorReferencia::getId).orElseGet(() -> crearProveedor(e.proveedor()));

        Map<String, ProductoReferencia> productos = new LinkedHashMap<>();
        for (int i = 0; i < e.productos().size(); i++) {
            DatosDemo.Producto p = e.productos().get(i);
            Optional<ProductoReferencia> previo = productoRepository.findBySku(p.sku());
            if (previo.isPresent() && !previo.get().isActivo()) {
                log.warn("Seed demo: el producto {} de {} fue dado de baja; se omiten los escenarios de compras.",
                        p.sku(), codigo);
                return Map.of();
            }
            int consecutivo = i + 1;
            long id;
            if (previo.isPresent()) {
                // Un producto que ya existe (creado por una ejecución anterior o a mano) conserva su stock.
                id = previo.get().getId();
            } else {
                id = crearProducto(p, consecutivo, proveedorId);
                if (p.stock() > 0) {
                    inventarioService.ajustar(new AjusteInventarioDTO(id, p.stock(), "Inventario inicial de demostración",
                            new BigDecimal(p.costo()), "demo-" + codigo + "-esc-ini-" + consecutivo));
                }
            }
            productos.put(p.sku(), productoRepository.findById(id).orElseThrow());
        }

        if (proveedorRepository.findByNit(e.proveedorDeBaja().nit()).isEmpty()) {
            proveedorService.eliminar(crearProveedor(e.proveedorDeBaja()));
        }
        if (productoRepository.findBySku(e.productoDeBaja().sku()).isEmpty()) {
            productoService.eliminar(crearProducto(e.productoDeBaja(), e.productos().size() + 1, proveedorId));
        }
        return productos;
    }

    private long crearProveedor(DatosDemo.Proveedor p) {
        return proveedorService.crear(new ProveedorRequestDTO(p.nit(), p.razonSocial(), p.email(), p.telefono(),
                p.direccion())).id();
    }

    /** El código de barras es el mismo en todas las empresas: es único por empresa, no global. */
    private long crearProducto(DatosDemo.Producto p, int consecutivo, long proveedorId) {
        return productoService.crear(new ProductoRequestDTO(p.sku(), DatosDemo.ean13(9, consecutivo), p.nombre(),
                "Producto ficticio de los escenarios de prueba.", new BigDecimal(p.precioCompra()), p.stockMinimo(),
                proveedorId)).id();
    }

    // ---------------------------------------------------------------- solicitudes

    /**
     * La crea el trabajador; el administrador la revisa y, si corresponde, genera su orden (que
     * recibe el mismo trabajador). Si ya existe una solicitud con esa justificación de ese
     * trabajador, no se hace nada; si el trabajador no está ACTIVO, se omite con un aviso.
     */
    private void solicitud(SolicitudDemo s, String codigo, Empresa empresa, Usuario admin, List<Usuario> trabajadores,
                           Map<String, ProductoReferencia> productos) {
        Usuario solicitante = trabajadores.get(s.solicitante());
        Specification<Solicitud> clave = (root, query, cb) -> cb.and(
                cb.equal(root.get("justificacion"), s.justificacion()),
                cb.equal(root.get("solicitante").get("id"), solicitante.getId()));
        if (!solicitudRepository.findAll(clave).isEmpty()) {
            return;
        }
        if (!activa(solicitante)) {
            log.warn("Seed demo: {} no está activo; se omite en {} la solicitud «{}».", solicitante.getUsername(), codigo,
                    s.justificacion());
            return;
        }
        List<DetalleSolicitudDTO> lineas = s.lineas().stream()
                .map(l -> new DetalleSolicitudDTO(productos.get(l.sku()).getId(), l.cantidad()))
                .toList();
        Long id = como(solicitante, empresa, () -> solicitudService.crear(new SolicitudRequestDTO(s.justificacion(), lineas)).id());
        if (s.estado() == EstadoSolicitud.PENDIENTE) {
            return;
        }
        solicitudService.cambiarEstado(id, new CambioEstadoSolicitudDTO(s.estado(), s.comentario()));
        switch (s.proceso()) {
            case NINGUNO -> {
                // Aprobada (o rechazada) sin orden.
            }
            case ORDEN_EN_BORRADOR -> ordenService.crearDesdeSolicitud(id);
            case ORDEN_RECIBIDA_EN_PARTE -> {
                OrdenCompraResponseDTO orden = ordenService.emitir(ordenService.crearDesdeSolicitud(id).get(0).id());
                programarEntrega(orden.id());
                // Llegan 5 de 10: 1 dañada y 2 faltantes informadas; quedan 5 pendientes.
                OrdenCompraResponseDTO.Linea linea = orden.detalles().get(0);
                como(solicitante, empresa, () -> recepcionService.registrar(orden.id(), new RecepcionRequestDTO(
                        "demo-" + codigo + "-esc-rec-1", "Entrega incompleta: una caja golpeada (demo)",
                        List.of(new LineaRecepcionDTO(linea.id(), 5)),
                        List.of(new IncidenciaRecepcionDTO(TipoIncidencia.DANADO, linea.id(), 1, "Caja golpeada (demo)"),
                                new IncidenciaRecepcionDTO(TipoIncidencia.FALTANTE, linea.id(), 2, null)))));
            }
        }
    }

    /** Dentro de una semana, por la mañana y confirmada por el proveedor: situación PROGRAMADA. */
    private void programarEntrega(Long ordenId) {
        entregaService.programar(ordenId, new ProgramacionEntregaRequestDTO(calendario.hoy().plusDays(7),
                LocalTime.of(8, 0), LocalTime.of(12, 0), true, "REM-DEMO-001", "Entrega por la mañana (demo)", null));
    }

    // ---------------------------------------------------------------- órdenes manuales

    /**
     * Borrador, cancelada tras emitirla y recibida por completo con un excedente. El borrador se
     * crea solo junto con la cancelada, en la misma transacción: aunque alguien edite después sus
     * observaciones (o lo emita o cancele), la cancelada sigue identificándolo y no se duplica.
     */
    private void ordenesManuales(String codigo, Empresa empresa, List<Usuario> trabajadores,
                                 Map<String, ProductoReferencia> productos) {
        ProductoReferencia b = productos.get("ESC-COM-02");
        ProductoReferencia a = productos.get("ESC-COM-01");
        if (!existe(ORDEN_CANCELADA)) {
            ordenService.crear(orden(ORDEN_BORRADOR, b, 4));
            OrdenCompraResponseDTO emitida = ordenService.emitir(ordenService.crear(orden(ORDEN_CANCELADA, a, 3)).id());
            ordenService.cancelar(emitida.id(), new CancelacionOrdenDTO("El proveedor no puede cumplir la fecha (demo)."));
        }
        if (existe(ORDEN_EXCEDENTE)) {
            return;
        }
        Usuario receptor = trabajadores.get(1);
        if (!activa(receptor)) {
            log.warn("Seed demo: {} no está activo; se omite en {} la orden recibida con excedente.",
                    receptor.getUsername(), codigo);
            return;
        }
        OrdenCompraResponseDTO emitida = ordenService.emitir(ordenService.crear(orden(ORDEN_EXCEDENTE, b, 12)).id());
        OrdenCompraResponseDTO.Linea linea = emitida.detalles().get(0);
        como(receptor, empresa, () -> recepcionService.registrar(emitida.id(), new RecepcionRequestDTO(
                "demo-" + codigo + "-esc-rec-2", "Llegó todo y dos unidades de más (demo)",
                List.of(new LineaRecepcionDTO(linea.id(), 12)),
                List.of(new IncidenciaRecepcionDTO(TipoIncidencia.EXCEDENTE, linea.id(), 2,
                        "Se devolverán al proveedor (demo)")))));
    }

    private boolean existe(String observaciones) {
        return !ordenRepository.findAll(conObservaciones(observaciones)).isEmpty();
    }

    private static OrdenCompraRequestDTO orden(String observaciones, ProductoReferencia producto, int cantidad) {
        return new OrdenCompraRequestDTO(producto.getProveedorId(), observaciones,
                List.of(new LineaOrdenCompraDTO(producto.getId(), cantidad, producto.getPrecio())));
    }
}
