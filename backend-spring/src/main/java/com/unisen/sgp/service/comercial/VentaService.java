package com.unisen.sgp.service.comercial;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.exception.ConflictoException;
import com.unisen.sgp.exception.ExistenciasInsuficientesException;
import com.unisen.sgp.exception.OperacionNoAutorizadaException;
import com.unisen.sgp.exception.RecursoNoEncontradoException;
import com.unisen.sgp.model.dto.VentaDTO;
import com.unisen.sgp.model.dto.VentaDTO.Cotizacion;
import com.unisen.sgp.model.dto.VentaDTO.CotizacionRequest;
import com.unisen.sgp.model.dto.VentaDTO.DescuentoRequest;
import com.unisen.sgp.model.dto.VentaDTO.Detalle;
import com.unisen.sgp.model.dto.VentaDTO.LineaCotizada;
import com.unisen.sgp.model.dto.VentaDTO.LineaRequest;
import com.unisen.sgp.model.dto.VentaDTO.PagoRequest;
import com.unisen.sgp.model.dto.VentaDTO.ProductoVenta;
import com.unisen.sgp.model.dto.VentaDTO.Resumen;
import com.unisen.sgp.model.dto.VentaDTO.VentaRequest;
import com.unisen.sgp.model.entity.AutorizacionComercial;
import com.unisen.sgp.model.entity.DetalleVenta;
import com.unisen.sgp.model.entity.EstadoStock;
import com.unisen.sgp.model.entity.EstadoVenta;
import com.unisen.sgp.model.entity.Existencia;
import com.unisen.sgp.model.entity.MedioPago;
import com.unisen.sgp.model.entity.MovimientoInventario;
import com.unisen.sgp.model.entity.PagoVenta;
import com.unisen.sgp.model.entity.PoliticaComercial;
import com.unisen.sgp.model.entity.ProductoReferencia;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.model.entity.Venta;
import com.unisen.sgp.model.entity.VentaBajoCosto;
import com.unisen.sgp.repository.AutorizacionComercialRepository;
import com.unisen.sgp.repository.DevolucionVentaRepository;
import com.unisen.sgp.repository.EmpresaRepository;
import com.unisen.sgp.repository.ExistenciaRepository;
import com.unisen.sgp.repository.ExistenciaRepository.Filtros;
import com.unisen.sgp.repository.MovimientoInventarioRepository;
import com.unisen.sgp.repository.ProductoReferenciaRepository;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.repository.VentaRepository;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.security.UsuarioActual;
import com.unisen.sgp.security.UsuarioPrincipal;
import com.unisen.sgp.service.CalendarioNegocio;
import com.unisen.sgp.service.InventarioService;
import com.unisen.sgp.service.ResultadoIdempotente;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Punto de venta de la empresa actual (@TenantId). Venden las personas de la empresa
 * (administrador comercial y trabajador); el SUPER_ADMIN solo consulta en supervisión.
 *
 * <p>Confirmar una venta es una sola transacción: bloquea la fila de la empresa (numeración y
 * claves de idempotencia), toma precios e IVA del catálogo, recalcula todo, valida descuentos y
 * autorizaciones, bloquea las existencias en orden de producto, descuenta, deja un movimiento por
 * línea con su costo histórico y guarda cabecera, líneas y pagos. Si algo falla no queda nada.
 */
@Service
@Transactional(readOnly = true)
public class VentaService {

    private static final Logger log = LoggerFactory.getLogger(VentaService.class);

    private final VentaRepository ventaRepository;
    private final DevolucionVentaRepository devolucionRepository;
    private final ProductoReferenciaRepository productoRepository;
    private final ExistenciaRepository existenciaRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final AutorizacionComercialRepository autorizacionRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final InventarioService inventarioService;
    private final ComercialService comercialService;
    private final CalendarioNegocio calendario;
    private final Clock clock;

    public VentaService(VentaRepository ventaRepository, DevolucionVentaRepository devolucionRepository,
                        ProductoReferenciaRepository productoRepository, ExistenciaRepository existenciaRepository,
                        MovimientoInventarioRepository movimientoRepository,
                        AutorizacionComercialRepository autorizacionRepository, EmpresaRepository empresaRepository,
                        UsuarioRepository usuarioRepository, InventarioService inventarioService,
                        ComercialService comercialService, CalendarioNegocio calendario, Clock clock) {
        this.ventaRepository = ventaRepository;
        this.devolucionRepository = devolucionRepository;
        this.productoRepository = productoRepository;
        this.existenciaRepository = existenciaRepository;
        this.movimientoRepository = movimientoRepository;
        this.autorizacionRepository = autorizacionRepository;
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.inventarioService = inventarioService;
        this.comercialService = comercialService;
        this.calendario = calendario;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ catálogo de la caja

    /** Productos a la venta (con precio) de la empresa, con su disponibilidad. Sin costos. */
    @PreAuthorize(Permisos.LECTURA_OPERATIVA)
    public Page<ProductoVenta> catalogo(String texto, boolean soloDisponibles, Pageable pageable) {
        TenantContextHolder.requerirEmpresa();
        Specification<Existencia> spec = Filtros.deProductosActivos().and(Filtros.aLaVenta())
                .and(Filtros.conTexto(texto == null || texto.isBlank() ? null : texto.strip()));
        if (soloDisponibles) {
            spec = spec.and((root, query, cb) -> cb.greaterThan(root.get("cantidad"), 0));
        }
        return existenciaRepository.findAll(spec, pageable).map(e -> productoVenta(e.getProducto(), e.getCantidad()));
    }

    /**
     * Escáner de la caja: código de barras exacto y, si no, SKU, en la empresa del contexto.
     *
     * @throws RecursoNoEncontradoException si no existe
     * @throws ConflictoException si existe pero no está a la venta (sin precio o dado de baja)
     */
    @PreAuthorize(Permisos.LECTURA_OPERATIVA)
    public ProductoVenta porCodigo(String codigo) {
        TenantContextHolder.requerirEmpresa();
        String limpio = codigo == null ? "" : codigo.strip();
        if (limpio.isEmpty() || limpio.length() > 64) {
            throw new CampoInvalidoException("codigo", "Indica un código de barras o SKU de hasta 64 caracteres.");
        }
        Optional<ProductoReferencia> encontrado = productoRepository.findByCodigoBarras(limpio)
                .filter(ProductoReferencia::isActivo)
                .or(() -> productoRepository.findBySku(limpio.toUpperCase(Locale.ROOT)).filter(ProductoReferencia::isActivo));
        ProductoReferencia producto = encontrado.orElseThrow(() -> RecursoNoEncontradoException.productoConCodigo(limpio));
        if (producto.getPrecioVenta() == null) {
            throw new ConflictoException("«" + producto.getNombre() + "» aún no tiene precio de venta.");
        }
        int unidades = existenciaRepository.findByProductoId(producto.getId()).map(Existencia::getCantidad).orElse(0);
        return productoVenta(producto, unidades);
    }

    // ------------------------------------------------------------------ cotización

    /**
     * Calcula el carrito como lo haría la venta, sin registrar ni bloquear nada (las existencias
     * pueden cambiar antes de confirmar: la confirmación vuelve a comprobarlo todo).
     */
    @PreAuthorize(Permisos.MIEMBRO_EMPRESA)
    public Cotizacion cotizar(CotizacionRequest dto) {
        TenantContextHolder.requerirEmpresa();
        UsuarioPrincipal vendedor = UsuarioActual.obtener();
        List<Long> ids = idsSinRepetir(dto.lineas());
        Map<Long, ProductoReferencia> productos = productosVendibles(ids);
        CalculadoraVenta.Resultado calculo = calcular(dto.lineas(), dto.descuentoGeneral(), productos);
        Map<Long, Existencia> existencias = existenciaRepository.findByProductoIdIn(ids).stream()
                .collect(Collectors.toMap(Existencia::getProductoId, Function.identity()));
        PoliticaComercial.Valores politica = comercialService.politica();
        Evaluacion evaluacion = evaluar(calculo, dto.lineas(), existencias, politica, vendedor.getRol());

        Boolean autorizacionValida = null;
        if (dto.codigoAutorizacion() != null) {
            autorizacionValida = autorizacionRepository.findByCodigo(ComercialService.normalizarCodigo(
                    dto.codigoAutorizacion())).map(a -> problemaAutorizacion(a, evaluacion) == null).orElse(false);
        }
        List<LineaCotizada> lineas = new ArrayList<>();
        boolean suficientes = true;
        for (int i = 0; i < calculo.lineas().size(); i++) {
            CalculadoraVenta.Linea l = calculo.lineas().get(i);
            ProductoReferencia p = productos.get(dto.lineas().get(i).productoId());
            int disponibles = Optional.ofNullable(existencias.get(p.getId())).map(Existencia::getCantidad).orElse(0);
            boolean suficiente = disponibles >= l.cantidad();
            suficientes &= suficiente;
            lineas.add(new LineaCotizada(l.numero(), p.getId(), p.getSku(), p.getNombre(), l.cantidad(), l.precioUnitario(),
                    l.subtotal(), l.descuentoLinea(), l.descuentoGeneral(), l.baseGravable(), l.tratamiento(), l.tarifa(),
                    l.impuesto(), l.total(), l.porcentajeDescuento(), disponibles, suficiente, evaluacion.bajoCosto().get(i)));
        }
        return new Cotizacion(lineas, calculo.subtotal(), calculo.descuentoLineas(), calculo.descuentoGeneral(),
                calculo.baseGravable(), calculo.impuestos(), calculo.total(), impuestosPorTarifa(calculo),
                calculo.descuentoMaximo(), evaluacion.limite(), suficientes, evaluacion.requiereAutorizacion(),
                evaluacion.motivos(), autorizacionValida, evaluacion.bajoCosto().contains(true));
    }

    // ------------------------------------------------------------------ confirmación

    /**
     * Registra la venta. Idempotente por {@code claveIdempotencia}: la misma clave con los mismos
     * datos devuelve la venta ya registrada (sin volver a descontar); con otros datos, 409.
     */
    @PreAuthorize(Permisos.MIEMBRO_EMPRESA)
    @Transactional
    public ResultadoIdempotente<Detalle> confirmar(VentaRequest dto) {
        Long empresaId = TenantContextHolder.requerirEmpresa();
        UsuarioPrincipal vendedor = UsuarioActual.obtener();
        List<Long> ids = idsSinRepetir(dto.lineas());
        String huella = huella(dto);

        // 1) Fila de la empresa bloqueada: numeración correlativa y claves sin carreras.
        empresaRepository.findWithLockById(empresaId)
                .orElseThrow(() -> new IllegalStateException("La empresa del contexto no existe: " + empresaId));
        Optional<Venta> previa = ventaRepository.findByClaveIdempotencia(dto.claveIdempotencia());
        if (previa.isPresent()) {
            if (!previa.get().getHuellaSolicitud().equals(huella)) {
                throw new ConflictoException("La clave de idempotencia ya se usó en otra venta con datos distintos.");
            }
            return new ResultadoIdempotente<>(detalle(previa.get()), false);
        }

        // 2) Precios e IVA del catálogo (nunca del cliente) y cálculo completo.
        Map<Long, ProductoReferencia> productos = productosVendibles(ids);
        CalculadoraVenta.Resultado calculo = calcular(dto.lineas(), dto.descuentoGeneral(), productos);

        // 3) Existencias bloqueadas en orden de producto (sin interbloqueos con recepciones y ajustes).
        Map<Long, Existencia> existencias = inventarioService.bloquear(ids.stream().sorted().toList());
        List<ExistenciasInsuficientesException.Faltante> faltantes = new ArrayList<>();
        for (LineaRequest linea : dto.lineas()) {
            Existencia e = existencias.get(linea.productoId());
            if (e.getCantidad() < linea.cantidad()) {
                faltantes.add(new ExistenciasInsuficientesException.Faltante(linea.productoId(),
                        productos.get(linea.productoId()).getNombre(), linea.cantidad(), e.getCantidad()));
            }
        }
        if (!faltantes.isEmpty()) {
            throw new ExistenciasInsuficientesException(faltantes);
        }

        // 4) Descuentos y ventas bajo el costo frente a la política y, si hace falta, la autorización.
        PoliticaComercial.Valores politica = comercialService.politica();
        Evaluacion evaluacion = evaluar(calculo, dto.lineas(), existencias, politica, vendedor.getRol());
        AutorizacionComercial autorizacion = null;
        if (evaluacion.requiereAutorizacion()) {
            if (dto.codigoAutorizacion() == null) {
                throw new OperacionNoAutorizadaException(String.join(" ", evaluacion.motivos())
                        + " Pide un código de autorización al administrador comercial.");
            }
            autorizacion = autorizacionRepository.findWithLockByCodigo(ComercialService.normalizarCodigo(dto.codigoAutorizacion()))
                    .orElseThrow(() -> new OperacionNoAutorizadaException("El código de autorización no existe."));
            String problema = problemaAutorizacion(autorizacion, evaluacion);
            if (problema != null) {
                throw new OperacionNoAutorizadaException(problema);
            }
        }

        // 5) Pagos: cubren exactamente el total.
        validarPagos(dto.pagos(), calculo.total());

        // 6) Registro: cabecera, líneas con su copia histórica, pagos, existencias y kárdex.
        Usuario usuario = usuarioRepository.getReferenceById(vendedor.getId());
        Instant ahora = ahora();
        BigDecimal costoTotal = BigDecimal.ZERO;
        boolean costoCompleto = true;
        for (int i = 0; i < calculo.lineas().size(); i++) {
            BigDecimal costo = existencias.get(dto.lineas().get(i).productoId()).getCostoPromedio();
            if (costo == null) {
                costoCompleto = false;
            } else {
                costoTotal = costoTotal.add(costo.multiply(BigDecimal.valueOf(calculo.lineas().get(i).cantidad())));
            }
        }
        DescuentoRequest general = dto.descuentoGeneral();
        Venta venta = new Venta(ventaRepository.ultimoNumero() + 1, dto.claveIdempotencia(), huella, dto.clienteNombre(),
                dto.clienteDocumento(), general == null ? null : general.tipo(), general == null ? null : general.valor(),
                new Venta.Totales(calculo.subtotal(), calculo.descuentoLineas(), calculo.descuentoGeneral(),
                        calculo.baseGravable(), calculo.impuestos(), calculo.total(),
                        costoCompleto ? Dinero.redondear(costoTotal) : null),
                autorizacion, usuario, ahora);
        for (int i = 0; i < calculo.lineas().size(); i++) {
            CalculadoraVenta.Linea l = calculo.lineas().get(i);
            LineaRequest pedida = dto.lineas().get(i);
            BigDecimal costo = existencias.get(pedida.productoId()).getCostoPromedio();
            venta.agregarLinea(new DetalleVenta(l.numero(), productos.get(pedida.productoId()), l.cantidad(),
                    l.precioUnitario(), pedida.descuento() == null ? null : pedida.descuento().tipo(),
                    pedida.descuento() == null ? null : pedida.descuento().valor(), l.subtotal(), l.descuentoLinea(),
                    l.descuentoGeneral(), l.baseGravable(), l.tratamiento(), l.tarifa(), l.impuesto(), l.total(), costo,
                    evaluacion.bajoCosto().get(i)));
        }
        for (PagoRequest pago : dto.pagos()) {
            venta.agregarPago(new PagoVenta(pago.medio(), pago.valor(), pago.recibido(), pago.referencia()));
        }
        Venta guardada = ventaRepository.saveAndFlush(venta);
        for (DetalleVenta linea : guardada.getDetalles()) {
            Existencia existencia = existencias.get(linea.getProducto().getId());
            int saldo = existencia.aplicar(-linea.getCantidad(), ahora);
            movimientoRepository.save(MovimientoInventario.deVenta(linea, saldo, usuario, ahora));
        }
        if (autorizacion != null) {
            autorizacion.usar(guardada.getId(), usuario, ahora);
        }
        movimientoRepository.flush();
        log.info("Venta {} registrada por {}: {} líneas, total {}{}", VentaDTO.numeroVisible(guardada.getNumero()),
                UsuarioActual.paraAuditoria(), guardada.getDetalles().size(), guardada.getTotal(),
                autorizacion == null ? "" : " (autorización " + autorizacion.getId() + ")");
        return new ResultadoIdempotente<>(detalle(guardada), true);
    }

    // ------------------------------------------------------------------ consultas

    /**
     * Historial: el trabajador ve solo sus ventas (se ignora cualquier otro vendedor pedido); la
     * gestión, todas las de la empresa. Fechas en la zona del negocio, ambas incluidas.
     */
    @PreAuthorize(Permisos.LECTURA_OPERATIVA)
    public Page<Resumen> listar(LocalDate desde, LocalDate hasta, EstadoVenta estado, Long vendedorId, Long numero,
                                Pageable pageable) {
        TenantContextHolder.requerirEmpresa();
        UsuarioPrincipal usuario = UsuarioActual.obtener();
        Long vendedor = usuario.getRol() == Rol.TRABAJADOR ? usuario.getId() : vendedorId;
        CalendarioNegocio.Intervalo intervalo = calendario.intervalo(desde, hasta, CalendarioNegocio.DIAS_MAXIMOS);
        Specification<Venta> spec = VentaRepositoryFiltros.de(intervalo, estado, vendedor, numero);
        return ventaRepository.findAll(spec, pageable).map(Resumen::from);
    }

    @PreAuthorize(Permisos.LECTURA_OPERATIVA)
    public Detalle obtener(Long id) {
        return detalle(visible(id));
    }

    /** La venta si quien pregunta puede verla; si no, 404 (sin revelar que existe). */
    public Venta visible(Long id) {
        TenantContextHolder.requerirEmpresa();
        UsuarioPrincipal usuario = UsuarioActual.obtener();
        Venta venta = ventaRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("venta", id));
        if (usuario.getRol() == Rol.TRABAJADOR && !venta.getVendidaPor().getId().equals(usuario.getId())) {
            throw new RecursoNoEncontradoException("venta", id);
        }
        return venta;
    }

    Detalle detalle(Venta venta) {
        return Detalle.from(venta, devolucionRepository.findByVentaIdOrderByRegistradaEnAscIdAsc(venta.getId()), verCostos());
    }

    /** Costos y rentabilidad: solo la gestión (administrador comercial o SUPER_ADMIN en supervisión). */
    static boolean verCostos() {
        return UsuarioActual.obtener().getRol() != Rol.TRABAJADOR;
    }

    // ------------------------------------------------------------------ cálculo y reglas

    record Evaluacion(BigDecimal limite, boolean superaLimite, boolean requiereBajoCosto, List<Boolean> bajoCosto,
                      List<String> motivos, CalculadoraVenta.Resultado calculo) {

        boolean requiereAutorizacion() {
            return superaLimite || requiereBajoCosto;
        }
    }

    private static Evaluacion evaluar(CalculadoraVenta.Resultado calculo, List<LineaRequest> lineas,
                                      Map<Long, Existencia> existencias, PoliticaComercial.Valores politica, Rol rol) {
        BigDecimal limite = politica.limitePara(rol);
        boolean supera = calculo.lineas().stream().anyMatch(l -> l.superaDescuento(limite));
        List<Boolean> bajoCosto = new ArrayList<>();
        for (int i = 0; i < calculo.lineas().size(); i++) {
            Existencia e = existencias.get(lineas.get(i).productoId());
            bajoCosto.add(calculo.lineas().get(i).bajoCosto(e == null ? null : e.getCostoPromedio()));
        }
        boolean requiereBajoCosto = politica.ventaBajoCosto() == VentaBajoCosto.AUTORIZACION && bajoCosto.contains(true);
        List<String> motivos = new ArrayList<>();
        if (supera) {
            motivos.add("El descuento supera tu límite (" + limite.stripTrailingZeros().toPlainString() + " %).");
        }
        if (requiereBajoCosto) {
            motivos.add("La venta queda por debajo del costo y la política exige autorización.");
        }
        return new Evaluacion(limite, supera, requiereBajoCosto, List.copyOf(bajoCosto), List.copyOf(motivos), calculo);
    }

    /** null si la autorización cubre la venta; si no, por qué. */
    private String problemaAutorizacion(AutorizacionComercial a, Evaluacion evaluacion) {
        if (a.usada()) {
            return "El código de autorización ya se usó en otra venta.";
        }
        if (a.vencida(ahora())) {
            return "El código de autorización venció. Pide uno nuevo.";
        }
        if (evaluacion.superaLimite()
                && evaluacion.calculo().lineas().stream().anyMatch(l -> l.superaDescuento(a.getDescuentoMaximo()))) {
            return "El descuento supera el autorizado (" + a.getDescuentoMaximo().stripTrailingZeros().toPlainString() + " %).";
        }
        if (evaluacion.requiereBajoCosto() && !a.isPermiteBajoCosto()) {
            return "La autorización no permite vender por debajo del costo.";
        }
        return null;
    }

    private static CalculadoraVenta.Resultado calcular(List<LineaRequest> lineas, DescuentoRequest general,
                                                       Map<Long, ProductoReferencia> productos) {
        List<CalculadoraVenta.Entrada> entradas = lineas.stream().map(l -> {
            ProductoReferencia p = productos.get(l.productoId());
            return new CalculadoraVenta.Entrada(p.getPrecioVenta(), l.cantidad(), p.getTratamientoIva(), p.getTarifaIva(),
                    l.descuento() == null ? null : new CalculadoraVenta.Descuento(l.descuento().tipo(), l.descuento().valor()));
        }).toList();
        return CalculadoraVenta.calcular(entradas,
                general == null ? null : new CalculadoraVenta.Descuento(general.tipo(), general.valor()));
    }

    private static List<Long> idsSinRepetir(List<LineaRequest> lineas) {
        Set<Long> vistos = new HashSet<>();
        for (int i = 0; i < lineas.size(); i++) {
            if (!vistos.add(lineas.get(i).productoId())) {
                throw new CampoInvalidoException("lineas[" + i + "].productoId",
                        "El producto está repetido: suma las cantidades en una sola línea.");
            }
        }
        return lineas.stream().map(LineaRequest::productoId).toList();
    }

    /** Productos activos de la empresa (el filtro @TenantId descarta los ajenos) y con precio de venta. */
    private Map<Long, ProductoReferencia> productosVendibles(List<Long> ids) {
        Map<Long, ProductoReferencia> productos = productoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ProductoReferencia::getId, Function.identity()));
        for (int i = 0; i < ids.size(); i++) {
            ProductoReferencia p = productos.get(ids.get(i));
            if (p == null || !p.isActivo()) {
                throw new CampoInvalidoException("lineas[" + i + "].productoId", "El producto no existe o fue dado de baja.");
            }
            if (p.getPrecioVenta() == null) {
                throw new CampoInvalidoException("lineas[" + i + "].productoId",
                        "«" + p.getNombre() + "» aún no tiene precio de venta.");
            }
        }
        return productos;
    }

    private static void validarPagos(List<PagoRequest> pagos, BigDecimal total) {
        BigDecimal suma = BigDecimal.ZERO;
        for (int i = 0; i < pagos.size(); i++) {
            PagoRequest p = pagos.get(i);
            if (p.medio() == MedioPago.EFECTIVO) {
                if (p.recibido() != null && p.recibido().compareTo(p.valor()) < 0) {
                    throw new CampoInvalidoException("pagos[" + i + "].recibido",
                            "Lo recibido en efectivo no puede ser menor que el valor del pago.");
                }
            } else if (p.recibido() != null) {
                throw new CampoInvalidoException("pagos[" + i + "].recibido", "Solo los pagos en efectivo llevan lo recibido.");
            }
            suma = suma.add(p.valor());
        }
        if (suma.compareTo(total) != 0) {
            throw new CampoInvalidoException("pagos", "Los pagos (" + Dinero.redondear(suma).toPlainString()
                    + ") deben sumar exactamente el total de la venta (" + total.toPlainString() + ").");
        }
    }

    private static List<VentaDTO.ImpuestoPorTarifa> impuestosPorTarifa(CalculadoraVenta.Resultado calculo) {
        Map<String, VentaDTO.ImpuestoPorTarifa> grupos = new java.util.TreeMap<>();
        for (CalculadoraVenta.Linea l : calculo.lineas()) {
            String clave = l.tratamiento().name() + "|" + l.tarifa().toPlainString();
            grupos.merge(clave, new VentaDTO.ImpuestoPorTarifa(l.tratamiento(), l.tarifa(), l.baseGravable(), l.impuesto()),
                    (a, b) -> new VentaDTO.ImpuestoPorTarifa(a.tratamiento(), a.tarifa(), a.base().add(b.base()),
                            a.impuesto().add(b.impuesto())));
        }
        return List.copyOf(grupos.values());
    }

    static ProductoVenta productoVenta(ProductoReferencia p, int unidades) {
        BigDecimal precio = p.getPrecioVenta();
        BigDecimal conIva = precio == null ? null
                : precio.add(p.getTratamientoIva().cobraImpuesto() ? Dinero.porcentajeDe(precio, p.getTarifaIva()) : Dinero.CERO);
        return new ProductoVenta(p.getId(), p.getSku(), p.getCodigoBarras(), p.getNombre(), precio, p.getTratamientoIva(),
                p.getTarifaIva(), conIva, unidades, EstadoStock.de(unidades, p.getStockMinimo()));
    }

    /** SHA-256 de la petición normalizada (lo que define la venta), para comparar reintentos. */
    static String huella(VentaRequest dto) {
        StringBuilder sb = new StringBuilder();
        for (LineaRequest l : dto.lineas()) {
            sb.append("L").append(l.productoId()).append(':').append(l.cantidad()).append(':').append(descuento(l.descuento()))
                    .append(';');
        }
        sb.append("G").append(descuento(dto.descuentoGeneral())).append(';');
        for (PagoRequest p : dto.pagos()) {
            sb.append("P").append(p.medio()).append(':').append(plano(p.valor())).append(':').append(plano(p.recibido()))
                    .append(':').append(Objects.toString(p.referencia(), "")).append(';');
        }
        sb.append("A").append(Objects.toString(ComercialService.normalizarCodigo(dto.codigoAutorizacion()), ""))
                .append(";C").append(Objects.toString(dto.clienteNombre(), "")).append('|')
                .append(Objects.toString(dto.clienteDocumento(), ""));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    private static String descuento(DescuentoRequest d) {
        return d == null ? "-" : d.tipo() + "=" + plano(d.valor());
    }

    private static String plano(BigDecimal valor) {
        return valor == null ? "-" : valor.stripTrailingZeros().toPlainString();
    }

    private Instant ahora() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** Filtros del historial a partir del intervalo de días validado. */
    static final class VentaRepositoryFiltros {

        private VentaRepositoryFiltros() {
        }

        static Specification<Venta> de(CalendarioNegocio.Intervalo intervalo, EstadoVenta estado, Long vendedor,
                                       Long numero) {
            return Specification.allOf(VentaRepository.Filtros.desde(intervalo.inicio()),
                    VentaRepository.Filtros.hasta(intervalo.fin()), VentaRepository.Filtros.conEstado(estado),
                    VentaRepository.Filtros.deVendedor(vendedor), VentaRepository.Filtros.conNumero(numero));
        }
    }
}
