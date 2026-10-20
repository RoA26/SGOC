package com.unisen.sgp.config.demo;

import com.unisen.sgp.model.dto.AjusteInventarioDTO;
import com.unisen.sgp.model.dto.ComercialDTO.CondicionesVentaRequest;
import com.unisen.sgp.model.dto.ComercialDTO.PoliticaRequest;
import com.unisen.sgp.model.dto.LineaOrdenCompraDTO;
import com.unisen.sgp.model.dto.LineaRecepcionDTO;
import com.unisen.sgp.model.dto.OrdenCompraRequestDTO;
import com.unisen.sgp.model.dto.OrdenCompraResponseDTO;
import com.unisen.sgp.model.dto.ProductoRequestDTO;
import com.unisen.sgp.model.dto.ProveedorRequestDTO;
import com.unisen.sgp.model.dto.RecepcionRequestDTO;
import com.unisen.sgp.model.dto.VentaDTO;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.MedioPago;
import com.unisen.sgp.model.entity.ProductoReferencia;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.TipoDescuento;
import com.unisen.sgp.model.entity.TratamientoIva;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.model.entity.VentaBajoCosto;
import com.unisen.sgp.repository.EmpresaRepository;
import com.unisen.sgp.repository.OrdenCompraRepository;
import com.unisen.sgp.repository.ProductoReferenciaRepository;
import com.unisen.sgp.repository.ProveedorRepository;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.repository.VentaRepository;
import com.unisen.sgp.security.CodigosInvitacion;
import com.unisen.sgp.security.UsuarioPrincipal;
import com.unisen.sgp.service.InventarioService;
import com.unisen.sgp.service.OrdenCompraService;
import com.unisen.sgp.service.ProductoService;
import com.unisen.sgp.service.ProveedorService;
import com.unisen.sgp.service.RecepcionService;
import com.unisen.sgp.service.UsuarioService;
import com.unisen.sgp.service.comercial.ComercialService;
import com.unisen.sgp.service.comercial.DevolucionVentaService;
import com.unisen.sgp.service.comercial.VentaService;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Datos de demostración reproducibles: cinco empresas ficticias con su administrador comercial,
 * trabajadores, proveedores, productos con precios e IVA de demostración, inventario inicial,
 * órdenes de compra (recibidas por completo, en parte y pendientes), ventas (con descuentos,
 * pagos mixtos y una bajo el costo) y una devolución parcial.
 *
 * <p>Solo existe con el perfil {@code demo}. Todo se hace con los servicios de la aplicación,
 * autenticado como las personas de cada empresa: las reglas, el kárdex y los costos son los
 * mismos que en el uso normal.
 *
 * <p><b>Idempotente</b>: cada empresa se identifica por su código (DEMO01…DEMO05) y la marca
 * {@code empresas.demo}; usuarios por username, proveedores por NIT, productos por SKU, inventario
 * inicial, recepciones, ventas y devoluciones por claves de idempotencia estables, y las órdenes
 * solo se crean si la empresa demo aún no tiene ninguna. Volver a ejecutarla no duplica nada, no
 * cambia contraseñas ni toca empresas que no sean de demostración (una empresa real con el mismo
 * código o NIT se respeta y esa empresa demo se omite). Cada empresa va en su propia transacción.
 */
@Service
@Profile("demo")
public class DemoSeedService {

    /** Contraseña común de TODAS las cuentas de demostración (solo desarrollo y pruebas). */
    static final String PASSWORD_DEMO = "abcde123!";

    private static final Logger log = LoggerFactory.getLogger(DemoSeedService.class);

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final ProveedorRepository proveedorRepository;
    private final ProveedorService proveedorService;
    private final ProductoReferenciaRepository productoRepository;
    private final ProductoService productoService;
    private final ComercialService comercialService;
    private final InventarioService inventarioService;
    private final OrdenCompraRepository ordenRepository;
    private final OrdenCompraService ordenService;
    private final RecepcionService recepcionService;
    private final VentaRepository ventaRepository;
    private final VentaService ventaService;
    private final DevolucionVentaService devolucionService;
    private final TransactionTemplate transaccion;

    public DemoSeedService(EmpresaRepository empresaRepository, UsuarioRepository usuarioRepository,
                           UsuarioService usuarioService, ProveedorRepository proveedorRepository,
                           ProveedorService proveedorService, ProductoReferenciaRepository productoRepository,
                           ProductoService productoService, ComercialService comercialService,
                           InventarioService inventarioService, OrdenCompraRepository ordenRepository,
                           OrdenCompraService ordenService, RecepcionService recepcionService,
                           VentaRepository ventaRepository, VentaService ventaService,
                           DevolucionVentaService devolucionService, TransactionTemplate transaccion) {
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.proveedorRepository = proveedorRepository;
        this.proveedorService = proveedorService;
        this.productoRepository = productoRepository;
        this.productoService = productoService;
        this.comercialService = comercialService;
        this.inventarioService = inventarioService;
        this.ordenRepository = ordenRepository;
        this.ordenService = ordenService;
        this.recepcionService = recepcionService;
        this.ventaRepository = ventaRepository;
        this.ventaService = ventaService;
        this.devolucionService = devolucionService;
        this.transaccion = transaccion;
    }

    /** Resultado por empresa: creada, completada (ya existía) u omitida (conflicto con datos reales). */
    public enum Resultado {
        CREADA, ACTUALIZADA, OMITIDA
    }

    public Map<String, Resultado> sembrar() {
        Map<String, Resultado> resultados = new LinkedHashMap<>();
        for (int i = 0; i < DatosDemo.EMPRESAS.size(); i++) {
            DatosDemo.Empresa datos = DatosDemo.EMPRESAS.get(i);
            int numero = i + 1;
            // 1) Empresa y cuentas (tablas globales), en su transacción.
            Alta alta = transaccion.execute(estado -> alta(datos));
            if (alta == null) {
                resultados.put(datos.codigo(), Resultado.OMITIDA);
                continue;
            }
            // 2) Datos de negocio: la transacción se abre YA dentro de la empresa (Hibernate fija el
            //    tenant de la sesión al abrirla), autenticada como sus personas.
            TenantContextHolder.conEmpresa(alta.empresaId(), () -> transaccion.execute(estado -> {
                Empresa empresa = empresaRepository.findById(alta.empresaId()).orElseThrow();
                Usuario admin = usuarioRepository.findById(alta.adminId()).orElseThrow();
                List<Usuario> trabajadores = usuarioRepository.findAllById(alta.trabajadorIds());
                como(admin, empresa, () -> {
                    catalogo(numero, datos);
                    compras(datos);
                    return null;
                });
                ventas(datos, admin, trabajadores, empresa);
                return null;
            }));
            resultados.put(datos.codigo(), alta.nueva() ? Resultado.CREADA : Resultado.ACTUALIZADA);
        }
        log.info("Seed de demostración terminada: {}", resultados);
        return resultados;
    }

    private record Alta(Long empresaId, Long adminId, List<Long> trabajadorIds, boolean nueva) {
    }

    /** Empresa y cuentas; null si la empresa debe omitirse (conflicto con datos reales). */
    private Alta alta(DatosDemo.Empresa datos) {
        Optional<Empresa> existente = empresaRepository.findByCodigo(datos.codigo());
        if (existente.isPresent() && !existente.get().isDemo()) {
            log.warn("Seed demo: el código {} pertenece a una empresa real; no se toca.", datos.codigo());
            return null;
        }
        if (existente.isEmpty() && empresaRepository.existsByNit(datos.nit())) {
            log.warn("Seed demo: el NIT {} ya pertenece a otra empresa; se omite {}.", datos.nit(), datos.codigo());
            return null;
        }
        Empresa empresa = existente.orElseGet(() -> {
            Empresa nueva = new Empresa(datos.nombre(), datos.nit(), datos.codigo(),
                    CodigosInvitacion.generarUnico(empresaRepository::existsByCodigoEmpresa));
            nueva.marcarDemo();
            return empresaRepository.saveAndFlush(nueva);
        });
        Usuario admin = usuario(datos.administrador(), Rol.ADMINISTRADOR_COMERCIAL, empresa, datos.codigo());
        List<Long> trabajadores = new ArrayList<>();
        for (DatosDemo.Persona p : datos.trabajadores()) {
            Usuario t = usuario(p, Rol.TRABAJADOR, empresa, datos.codigo());
            if (t == null) {
                admin = null;
                break;
            }
            trabajadores.add(t.getId());
        }
        if (admin == null) {
            log.warn("Seed demo: algún usuario de {} ya existe en otra empresa; se omite.", datos.codigo());
            return null;
        }
        return new Alta(empresa.getId(), admin.getId(), trabajadores, existente.isEmpty());
    }

    /** Crea la cuenta si no existe; si existe en esta empresa la reutiliza (sin tocar su contraseña). */
    private Usuario usuario(DatosDemo.Persona p, Rol rol, Empresa empresa, String codigo) {
        Optional<Usuario> existente = usuarioRepository.findByUsername(p.usuario());
        if (existente.isPresent()) {
            Usuario u = existente.get();
            return u.getEmpresa() != null && empresa.getId().equals(u.getEmpresa().getId()) ? u : null;
        }
        String correo = p.usuario().replace('.', '-') + "@" + codigo.toLowerCase() + ".sgp-demo.test";
        return usuarioService.crearUsuario(p.usuario(), correo, p.nombres(), p.apellidos(), PASSWORD_DEMO, rol, empresa);
    }

    private void catalogo(int numero, DatosDemo.Empresa datos) {
        if (comercialService.consultarPolitica().porDefecto()) {
            // Valores SOLO de demostración (no son una política recomendada): trabajador 10 %, administrador 30 %.
            comercialService.actualizarPolitica(new PoliticaRequest(new BigDecimal("10"), new BigDecimal("30"),
                    VentaBajoCosto.PERMITIR, new BigDecimal("30")));
        }
        List<Long> proveedores = new ArrayList<>();
        for (DatosDemo.Proveedor p : datos.proveedores()) {
            proveedores.add(proveedorRepository.findByNit(p.nit()).map(prov -> prov.getId())
                    .orElseGet(() -> proveedorService.crear(new ProveedorRequestDTO(p.nit(), p.razonSocial(), p.email(),
                            p.telefono(), p.direccion())).id()));
        }
        for (int i = 0; i < datos.productos().size(); i++) {
            DatosDemo.Producto p = datos.productos().get(i);
            long proveedor = proveedores.get(i % proveedores.size());
            String codigoBarras = DatosDemo.ean13(numero, i + 1);
            Optional<ProductoReferencia> existente = productoRepository.findBySku(p.sku());
            if (existente.isPresent() && !existente.get().isActivo()) {
                continue;
            }
            long id = existente.map(ProductoReferencia::getId).orElseGet(() -> productoService.crear(new ProductoRequestDTO(
                    p.sku(), codigoBarras, p.nombre(), "Producto ficticio de demostración.",
                    new BigDecimal(p.precioCompra()), p.stockMinimo(), proveedor)).id());
            if (existente.isEmpty() || existente.get().getPrecioVenta() == null) {
                comercialService.cambiarCondiciones(id, new CondicionesVentaRequest(new BigDecimal(p.precioVenta()),
                        TratamientoIva.valueOf(p.tratamiento()), new BigDecimal(p.tarifa()),
                        "Precio inicial de demostración (IVA ficticio)"));
            }
            if (p.stock() > 0) {
                inventarioService.ajustar(new AjusteInventarioDTO(id, p.stock(), "Inventario inicial de demostración",
                        p.costo() == null ? null : new BigDecimal(p.costo()), "demo-" + datos.codigo() + "-ini-" + (i + 1)));
            }
        }
    }

    /** Tres órdenes: recibida por completo, recibida en parte y emitida pendiente (solo la primera vez). */
    private void compras(DatosDemo.Empresa datos) {
        if (ordenRepository.count() > 0) {
            return;
        }
        // Los agotados de la demo no se reponen: se quedan agotados para mostrarlo.
        java.util.Set<String> agotados = new java.util.HashSet<>();
        datos.productos().stream().filter(p -> p.stock() == 0).forEach(p -> agotados.add(p.sku()));
        List<ProductoReferencia> productos = productosDemo(datos).stream().filter(p -> !agotados.contains(p.getSku())).toList();
        // Agrupados por proveedor (una orden admite un solo proveedor).
        Map<Long, List<ProductoReferencia>> porProveedor = new LinkedHashMap<>();
        productos.forEach(p -> porProveedor.computeIfAbsent(p.getProveedorId(), k -> new ArrayList<>()).add(p));
        List<List<ProductoReferencia>> grupos = new ArrayList<>(porProveedor.values());
        List<ProductoReferencia> g1 = grupos.get(0);
        List<ProductoReferencia> g2 = grupos.size() > 1 ? grupos.get(1) : g1;

        OrdenCompraResponseDTO completa = emitir(g1.get(0).getProveedorId(), List.of(g1.get(0), g1.get(1)), 10,
                "Reposición mensual (demo)");
        recibir(datos, 1, completa, false);
        OrdenCompraResponseDTO parcial = emitir(g2.get(0).getProveedorId(), List.of(g2.get(0), g2.get(g2.size() - 1)), 12,
                "Pedido con entrega parcial (demo)");
        recibir(datos, 2, parcial, true);
        emitir(g1.get(0).getProveedorId(), List.of(g1.get(g1.size() - 1)), 6, "Pedido pendiente de entrega (demo)");
    }

    private OrdenCompraResponseDTO emitir(Long proveedorId, List<ProductoReferencia> productos, int cantidad,
                                          String observaciones) {
        List<LineaOrdenCompraDTO> lineas = productos.stream()
                .map(p -> new LineaOrdenCompraDTO(p.getId(), cantidad,
                        p.getPrecio().multiply(new BigDecimal("1.05")).setScale(0, java.math.RoundingMode.HALF_UP)))
                .toList();
        OrdenCompraResponseDTO borrador = ordenService.crear(new OrdenCompraRequestDTO(proveedorId, observaciones, lineas));
        return ordenService.emitir(borrador.id());
    }

    private void recibir(DatosDemo.Empresa datos, int n, OrdenCompraResponseDTO orden, boolean parcial) {
        List<LineaRecepcionDTO> lineas = orden.detalles().stream()
                .map(l -> new LineaRecepcionDTO(l.id(), parcial ? l.cantidad() / 2 : l.cantidad()))
                .toList();
        recepcionService.registrar(orden.id(), new RecepcionRequestDTO("demo-" + datos.codigo() + "-rec-" + n,
                parcial ? "Llegó la mitad; el resto queda pendiente (demo)" : "Entrega completa (demo)", lineas, List.of()));
    }

    /**
     * Ventas de demostración con claves estables: sin descuento, con descuento de línea dentro del
     * límite, con descuento general, bajo el costo, con pagos mixtos; y una devolución parcial.
     */
    private void ventas(DatosDemo.Empresa datos, Usuario admin, List<Usuario> trabajadores, Empresa empresa) {
        List<ProductoReferencia> vendibles = como(admin, empresa, () -> productosDemo(datos).stream()
                .filter(p -> p.getPrecioVenta() != null).toList());
        List<ProductoReferencia> conStock = como(admin, empresa, () -> vendibles.stream()
                .filter(p -> ventaService.porCodigo(p.getSku()).existencias() > 2).toList());
        if (conStock.size() < 3) {
            return;
        }
        ProductoReferencia bajoCosto = datos.productos().stream()
                .filter(p -> p.costo() != null && new BigDecimal(p.precioVenta()).compareTo(new BigDecimal(p.costo())) < 0)
                .findFirst().flatMap(p -> conStock.stream().filter(s -> s.getSku().equals(p.sku())).findFirst())
                .orElse(conStock.get(conStock.size() - 1));
        Usuario t1 = trabajadores.get(0);
        Usuario t2 = trabajadores.get(trabajadores.size() > 1 ? 1 : 0);
        String c = "demo-" + datos.codigo() + "-v-";

        Long primera = vender(t1, empresa, c + 1, List.of(linea(conStock.get(0), 2, null), linea(conStock.get(1), 1, null)),
                null, MedioPago.EFECTIVO, true);
        vender(t2, empresa, c + 2, List.of(linea(conStock.get(2), 1, new VentaDTO.DescuentoRequest(TipoDescuento.PORCENTAJE,
                new BigDecimal("5")))), null, MedioPago.TARJETA_DEBITO, false);
        vender(admin, empresa, c + 3, List.of(linea(conStock.get(0), 1, new VentaDTO.DescuentoRequest(TipoDescuento.PORCENTAJE,
                        new BigDecimal("20"))), linea(conStock.get(1), 2, null)),
                new VentaDTO.DescuentoRequest(TipoDescuento.PORCENTAJE, new BigDecimal("2")), MedioPago.TRANSFERENCIA, false);
        vender(t1, empresa, c + 4, List.of(linea(bajoCosto, 1, null)), null, MedioPago.EFECTIVO, false);
        vender(t2, empresa, c + 5, List.of(linea(conStock.get(2), 2, null), linea(conStock.get(0), 1, null)), null,
                null, false);

        String claveDevolucion = "demo-" + datos.codigo() + "-dev-1";
        if (primera != null) {
            como(admin, empresa, () -> {
                var venta = ventaService.obtener(primera);
                if (venta.devoluciones().isEmpty()) {
                    devolucionService.devolver(primera, new VentaDTO.DevolucionRequest(claveDevolucion,
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
            VentaDTO.Cotizacion cotizacion = ventaService.cotizar(new VentaDTO.CotizacionRequest(lineas, general, null));
            if (!cotizacion.existenciasSuficientes() || cotizacion.requiereAutorizacion()) {
                log.info("Seed demo: venta {} omitida (sin existencias o requiere autorización).", clave);
                return null;
            }
            BigDecimal total = cotizacion.total();
            List<VentaDTO.PagoRequest> pagos;
            if (medio == null) {
                BigDecimal tarjeta = total.divide(BigDecimal.TWO, 0, java.math.RoundingMode.DOWN);
                pagos = List.of(new VentaDTO.PagoRequest(MedioPago.TARJETA_CREDITO, tarjeta, null, "DEMO-" + clave.hashCode()),
                        new VentaDTO.PagoRequest(MedioPago.EFECTIVO, total.subtract(tarjeta), null, null));
            } else if (medio == MedioPago.EFECTIVO && conCambio) {
                BigDecimal recibido = total.divide(new BigDecimal("10000"), 0, java.math.RoundingMode.UP)
                        .multiply(new BigDecimal("10000"));
                pagos = List.of(new VentaDTO.PagoRequest(medio, total, recibido, null));
            } else {
                pagos = List.of(new VentaDTO.PagoRequest(medio, total, null, null));
            }
            return ventaService.confirmar(new VentaDTO.VentaRequest(clave, lineas, general, pagos, null,
                    "Cliente de mostrador (demo)", null)).valor().id();
        });
    }

    private List<ProductoReferencia> productosDemo(DatosDemo.Empresa datos) {
        List<ProductoReferencia> productos = new ArrayList<>();
        for (DatosDemo.Producto p : datos.productos()) {
            productoRepository.findBySku(p.sku()).filter(ProductoReferencia::isActivo).ifPresent(productos::add);
        }
        return productos;
    }

    /** Ejecuta {@code accion} autenticado como {@code usuario} dentro de su empresa, y restaura el contexto. */
    private <T> T como(Usuario usuario, Empresa empresa, Supplier<T> accion) {
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
