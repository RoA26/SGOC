package com.unisen.sgp.service.comercial;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.exception.RecursoNoEncontradoException;
import com.unisen.sgp.model.dto.ComercialDTO.AutorizacionDTO;
import com.unisen.sgp.model.dto.ComercialDTO.AutorizacionRequest;
import com.unisen.sgp.model.dto.ComercialDTO.CambioPrecioDTO;
import com.unisen.sgp.model.dto.ComercialDTO.CondicionesVentaRequest;
import com.unisen.sgp.model.dto.ComercialDTO.PoliticaDTO;
import com.unisen.sgp.model.dto.ComercialDTO.PoliticaRequest;
import com.unisen.sgp.model.dto.ComercialDTO.ProductoComercial;
import com.unisen.sgp.model.dto.UsuarioResumenDTO;
import com.unisen.sgp.model.entity.AutorizacionComercial;
import com.unisen.sgp.model.entity.CambioPrecio;
import com.unisen.sgp.model.entity.EstadoStock;
import com.unisen.sgp.model.entity.Existencia;
import com.unisen.sgp.model.entity.PoliticaComercial;
import com.unisen.sgp.model.entity.Producto;
import com.unisen.sgp.model.entity.ProductoReferencia;
import com.unisen.sgp.model.entity.TratamientoIva;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.AutorizacionComercialRepository;
import com.unisen.sgp.repository.CambioPrecioRepository;
import com.unisen.sgp.repository.ExistenciaRepository;
import com.unisen.sgp.repository.ExistenciaRepository.Filtros;
import com.unisen.sgp.repository.PoliticaComercialRepository;
import com.unisen.sgp.repository.ProductoReferenciaRepository;
import com.unisen.sgp.repository.ProductoRepository;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.security.UsuarioActual;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión comercial de la empresa actual (@TenantId): condiciones de venta (precio sin IVA y
 * tratamiento del IVA) con historial, rentabilidad unitaria, política comercial y autorizaciones
 * de excepción.
 *
 * <p>Consultan la gestión (el administrador comercial y el SUPER_ADMIN supervisando una empresa;
 * en el modo global, 400: costos y precios se consultan empresa por empresa); solo el
 * administrador comercial de la empresa cambia precios, IVA, política o emite autorizaciones: el
 * SUPER_ADMIN no recibe ninguna autorización comercial implícita (las FK de autoría lo impiden
 * también en la BD).
 */
@Service
@Transactional(readOnly = true)
public class ComercialService {

    private static final Logger log = LoggerFactory.getLogger(ComercialService.class);
    private static final String ALFABETO_CODIGO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    public enum FiltroComercial {
        TODOS, A_LA_VENTA, SIN_PRECIO, SIN_COSTO, BAJO_COSTO
    }

    private final ProductoRepository productoRepository;
    private final ProductoReferenciaRepository productoReferenciaRepository;
    private final ExistenciaRepository existenciaRepository;
    private final CambioPrecioRepository cambioRepository;
    private final PoliticaComercialRepository politicaRepository;
    private final AutorizacionComercialRepository autorizacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    public ComercialService(ProductoRepository productoRepository,
                            ProductoReferenciaRepository productoReferenciaRepository,
                            ExistenciaRepository existenciaRepository, CambioPrecioRepository cambioRepository,
                            PoliticaComercialRepository politicaRepository,
                            AutorizacionComercialRepository autorizacionRepository,
                            UsuarioRepository usuarioRepository, Clock clock) {
        this.productoRepository = productoRepository;
        this.productoReferenciaRepository = productoReferenciaRepository;
        this.existenciaRepository = existenciaRepository;
        this.cambioRepository = cambioRepository;
        this.politicaRepository = politicaRepository;
        this.autorizacionRepository = autorizacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ productos y precios

    @PreAuthorize(Permisos.GESTION)
    public Page<ProductoComercial> productos(String texto, FiltroComercial filtro, Pageable pageable) {
        TenantContextHolder.requerirEmpresa();
        Specification<Existencia> spec = Filtros.deProductosActivos().and(Filtros.conTexto(limpiar(texto)));
        spec = switch (filtro == null ? FiltroComercial.TODOS : filtro) {
            case TODOS -> spec;
            case A_LA_VENTA -> spec.and(Filtros.aLaVenta());
            case SIN_PRECIO -> spec.and(Filtros.sinPrecioVenta());
            case SIN_COSTO -> spec.and(Filtros.sinCosto());
            case BAJO_COSTO -> spec.and(Filtros.bajoCosto());
        };
        BigDecimal margenObjetivo = politica().margenObjetivo();
        return existenciaRepository.findAll(spec, pageable).map(e -> productoComercial(e.getProducto(), e, margenObjetivo));
    }

    @PreAuthorize(Permisos.GESTION)
    public ProductoComercial producto(Long id) {
        TenantContextHolder.requerirEmpresa();
        ProductoReferencia producto = productoReferenciaRepository.findById(id).filter(ProductoReferencia::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("producto", id));
        Existencia existencia = existenciaRepository.findByProductoId(id).orElse(null);
        return productoComercial(producto, existencia, politica().margenObjetivo());
    }

    /**
     * Cambia el precio de venta y el tratamiento del IVA de un producto de la empresa. Bloquea el
     * producto: dos cambios simultáneos quedan en el historial uno detrás de otro, cada uno con el
     * valor anterior exacto. Si nada cambia, no se registra nada (idempotente). Un precio por
     * debajo del costo se permite y se advierte en la respuesta ({@code bajoCosto}).
     */
    @PreAuthorize(Permisos.ADMINISTRADOR_COMERCIAL)
    @Transactional
    public ProductoComercial cambiarCondiciones(Long productoId, CondicionesVentaRequest dto) {
        TenantContextHolder.requerirEmpresa();
        validarTarifa(dto.tratamientoIva(), dto.tarifaIva());
        Producto producto = productoRepository.findWithLockById(productoId).filter(Producto::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("producto", productoId));
        BigDecimal precioAnterior = producto.getPrecioVenta();
        TratamientoIva tratamientoAnterior = producto.getTratamientoIva();
        BigDecimal tarifaAnterior = producto.getTarifaIva();
        boolean sinCambios = precioAnterior != null && precioAnterior.compareTo(dto.precioVenta()) == 0
                && tratamientoAnterior == dto.tratamientoIva() && tarifaAnterior.compareTo(dto.tarifaIva()) == 0;
        Existencia existencia = existenciaRepository.findByProductoId(productoId).orElse(null);
        if (!sinCambios) {
            producto.definirCondicionesVenta(dto.precioVenta(), dto.tratamientoIva(), dto.tarifaIva());
            productoRepository.saveAndFlush(producto);
            ProductoReferencia referencia = productoReferenciaRepository.findById(productoId).orElseThrow();
            cambioRepository.save(new CambioPrecio(referencia, precioAnterior, tratamientoAnterior, tarifaAnterior,
                    producto, existencia == null ? null : existencia.getCostoPromedio(), dto.motivo(), usuarioActual(),
                    ahora()));
            log.info("Condiciones de venta del producto {} cambiadas por {}: {} → {} ({} {} %)", producto.getSku(),
                    UsuarioActual.paraAuditoria(), precioAnterior, producto.getPrecioVenta(), producto.getTratamientoIva(),
                    producto.getTarifaIva());
        }
        return producto(productoId);
    }

    @PreAuthorize(Permisos.GESTION)
    public Page<CambioPrecioDTO> historial(Long productoId, Pageable pageable) {
        TenantContextHolder.requerirEmpresa();
        if (productoId != null) {
            productoReferenciaRepository.findById(productoId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("producto", productoId));
            return cambioRepository.findByProductoId(productoId, pageable).map(CambioPrecioDTO::from);
        }
        return cambioRepository.findAll(pageable).map(CambioPrecioDTO::from);
    }

    // ------------------------------------------------------------------ política

    /**
     * Valores vigentes de la política de la empresa del contexto (los de por defecto si no definió
     * los suyos). Nunca sin empresa: en el modo global no hay "una" política.
     */
    public PoliticaComercial.Valores politica() {
        TenantContextHolder.requerirEmpresa();
        return politicaRepository.findFirstByOrderByIdAsc().map(PoliticaComercial::valores)
                .orElse(PoliticaComercial.POR_DEFECTO);
    }

    @PreAuthorize(Permisos.GESTION)
    public PoliticaDTO consultarPolitica() {
        TenantContextHolder.requerirEmpresa();
        return politicaRepository.findFirstByOrderByIdAsc()
                .map(p -> dto(p.valores(), false, UsuarioResumenDTO.from(p.getActualizadaPor()), p.getActualizadaEn()))
                .orElseGet(() -> dto(PoliticaComercial.POR_DEFECTO, true, null, null));
    }

    @PreAuthorize(Permisos.ADMINISTRADOR_COMERCIAL)
    @Transactional
    public PoliticaDTO actualizarPolitica(PoliticaRequest dto) {
        TenantContextHolder.requerirEmpresa();
        if (dto.descuentoMaxTrabajador().compareTo(dto.descuentoMaxAdministrador()) > 0) {
            throw new CampoInvalidoException("descuentoMaxTrabajador",
                    "El límite del trabajador no puede superar el del administrador comercial.");
        }
        PoliticaComercial.Valores valores = new PoliticaComercial.Valores(dto.descuentoMaxTrabajador(),
                dto.descuentoMaxAdministrador(), dto.ventaBajoCosto(), dto.margenObjetivo());
        Usuario usuario = usuarioActual();
        Instant ahora = ahora();
        PoliticaComercial politica = politicaRepository.findFirstByOrderByIdAsc()
                .map(p -> {
                    p.actualizar(valores, usuario, ahora);
                    return p;
                })
                .orElseGet(() -> new PoliticaComercial(valores, usuario, ahora));
        politicaRepository.saveAndFlush(politica);
        log.info("Política comercial actualizada por {}: trabajador {} %, administrador {} %, bajo costo {}",
                UsuarioActual.paraAuditoria(), valores.descuentoMaxTrabajador(), valores.descuentoMaxAdministrador(),
                valores.ventaBajoCosto());
        return consultarPolitica();
    }

    private static PoliticaDTO dto(PoliticaComercial.Valores v, boolean porDefecto, UsuarioResumenDTO por, Instant en) {
        return new PoliticaDTO(v.descuentoMaxTrabajador(), v.descuentoMaxAdministrador(), v.ventaBajoCosto(),
                v.margenObjetivo(), porDefecto, por, en);
    }

    // ------------------------------------------------------------------ autorizaciones

    /**
     * Emite un código de un solo uso para una venta con un descuento por encima del límite de quien
     * vende o bajo el costo. No puede autorizar más descuento que el límite del propio administrador.
     */
    @PreAuthorize(Permisos.ADMINISTRADOR_COMERCIAL)
    @Transactional
    public AutorizacionDTO crearAutorizacion(AutorizacionRequest dto) {
        TenantContextHolder.requerirEmpresa();
        BigDecimal limite = politica().descuentoMaxAdministrador();
        if (dto.descuentoMaximo().compareTo(limite) > 0) {
            throw new CampoInvalidoException("descuentoMaximo",
                    "No puedes autorizar más descuento que tu propio límite (" + limite.stripTrailingZeros().toPlainString()
                            + " %).");
        }
        Instant ahora = ahora();
        String codigo;
        do {
            codigo = generarCodigo();
        } while (autorizacionRepository.existsByCodigo(codigo));
        AutorizacionComercial autorizacion = autorizacionRepository.saveAndFlush(new AutorizacionComercial(codigo,
                dto.descuentoMaximo(), dto.permiteBajoCosto(), dto.motivo(), usuarioActual(), ahora,
                ahora.plus(Duration.ofMinutes(dto.minutosVigencia()))));
        log.info("Autorización comercial {} emitida por {} (descuento hasta {} %, bajo costo: {})", autorizacion.getId(),
                UsuarioActual.paraAuditoria(), dto.descuentoMaximo(), dto.permiteBajoCosto());
        return AutorizacionDTO.from(autorizacion, true, ahora);
    }

    @PreAuthorize(Permisos.GESTION)
    public Page<AutorizacionDTO> autorizaciones(Pageable pageable) {
        TenantContextHolder.requerirEmpresa();
        Instant ahora = ahora();
        return autorizacionRepository.findAll(pageable).map(a -> AutorizacionDTO.from(a, false, ahora));
    }

    /** "ABCD-2345": 8 caracteres sin los ambiguos (0/O, 1/I/L). */
    static String generarCodigo() {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(ALFABETO_CODIGO.charAt(ALEATORIO.nextInt(ALFABETO_CODIGO.length())));
        }
        return sb.toString();
    }

    /** Normaliza lo que teclea el vendedor: mayúsculas, sin espacios, con el guion en su sitio. */
    public static String normalizarCodigo(String entrada) {
        if (entrada == null) {
            return null;
        }
        String limpio = entrada.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return limpio.length() == 8 ? limpio.substring(0, 4) + "-" + limpio.substring(4) : limpio;
    }

    // ------------------------------------------------------------------ ayudas

    static void validarTarifa(TratamientoIva tratamiento, BigDecimal tarifa) {
        if (tratamiento.cobraImpuesto() && tarifa.signum() <= 0) {
            throw new CampoInvalidoException("tarifaIva", "Un producto gravado necesita una tarifa mayor que 0 %.");
        }
        if (!tratamiento.cobraImpuesto() && tarifa.signum() != 0) {
            throw new CampoInvalidoException("tarifaIva",
                    "Los productos exentos o excluidos no cobran IVA: la tarifa debe ser 0 %.");
        }
    }

    static ProductoComercial productoComercial(ProductoReferencia p, Existencia existencia, BigDecimal margenObjetivo) {
        BigDecimal costo = existencia == null ? null : existencia.getCostoPromedio();
        int unidades = existencia == null ? 0 : existencia.getCantidad();
        BigDecimal precio = p.getPrecioVenta();
        Rentabilidad r = precio == null ? null : Rentabilidad.de(precio, costo);
        BigDecimal precioConIva = precio == null ? null
                : precio.add(p.getTratamientoIva().cobraImpuesto() ? Dinero.porcentajeDe(precio, p.getTarifaIva()) : Dinero.CERO);
        return new ProductoComercial(p.getId(), p.getSku(), p.getCodigoBarras(), p.getNombre(), p.isActivo(),
                p.getPrecio(), precio, p.getTratamientoIva(), p.getTarifaIva(), precioConIva, costo,
                r == null ? null : r.utilidad(), r == null ? null : r.margen(), r == null ? null : r.recargo(),
                r != null && r.perdida(), Rentabilidad.precioSugerido(costo, margenObjetivo), unidades,
                EstadoStock.de(unidades, p.getStockMinimo()));
    }

    private static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    private Usuario usuarioActual() {
        return usuarioRepository.getReferenceById(Objects.requireNonNull(UsuarioActual.obtener().getId()));
    }

    private Instant ahora() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
