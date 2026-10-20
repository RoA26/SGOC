package com.unisen.sgp.service.comercial;

import com.unisen.sgp.model.dto.ReporteVentasDTO;
import com.unisen.sgp.model.dto.ReporteVentasDTO.LineaBajoCosto;
import com.unisen.sgp.model.dto.ReporteVentasDTO.ProductoBajoInventario;
import com.unisen.sgp.model.dto.ReporteVentasDTO.ProductoVendido;
import com.unisen.sgp.model.dto.ReporteVentasDTO.PuntoIngreso;
import com.unisen.sgp.model.dto.ReporteVentasDTO.Reporte;
import com.unisen.sgp.model.dto.ReporteVentasDTO.Resumen;
import com.unisen.sgp.model.dto.ReporteVentasDTO.Vendedor;
import com.unisen.sgp.model.dto.VentaDTO;
import com.unisen.sgp.model.entity.EstadoStock;
import com.unisen.sgp.model.entity.EstadoVenta;
import com.unisen.sgp.model.entity.Existencia;
import com.unisen.sgp.model.entity.MetodoCosto;
import com.unisen.sgp.repository.ExistenciaRepository;
import com.unisen.sgp.repository.ExistenciaRepository.Filtros;
import com.unisen.sgp.repository.ReporteVentasRepository;
import com.unisen.sgp.repository.ReporteVentasRepository.FilaDevolucion;
import com.unisen.sgp.repository.ReporteVentasRepository.FilaProducto;
import com.unisen.sgp.repository.ReporteVentasRepository.FilaVenta;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.security.UsuarioActual;
import com.unisen.sgp.service.CalendarioNegocio;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reportes comerciales de la empresa del contexto, calculados con los datos históricos de las
 * ventas y devoluciones (nunca con los precios o costos vigentes): los mismos datos dan siempre
 * los mismos resultados, y coinciden con el detalle de cada venta ({@link Rentabilidad}).
 *
 * <p>Con rentabilidad: solo la gestión (administrador comercial; SUPER_ADMIN en supervisión). El
 * trabajador solo consulta sus ventas del día, sin costos.
 */
@Service
@Transactional(readOnly = true)
public class ReporteVentasService {

    private static final int TOP = 10;
    private static final int MAX_BAJO_COSTO = 50;
    private static final int MAX_BAJO_INVENTARIO = 20;

    private final ReporteVentasRepository repository;
    private final ExistenciaRepository existenciaRepository;
    private final CalendarioNegocio calendario;

    public ReporteVentasService(ReporteVentasRepository repository, ExistenciaRepository existenciaRepository,
                                CalendarioNegocio calendario) {
        this.repository = repository;
        this.existenciaRepository = existenciaRepository;
        this.calendario = calendario;
    }

    @PreAuthorize(Permisos.GESTION)
    public Reporte reporte(LocalDate desde, LocalDate hasta) {
        TenantContextHolder.requerirEmpresa();
        CalendarioNegocio.Periodo periodo = calendario.periodo(desde, hasta);
        List<FilaVenta> ventas = repository.ventas(periodo.inicio(), periodo.fin());
        List<FilaDevolucion> devoluciones = repository.devoluciones(periodo.inicio(), periodo.fin());

        long numero = 0;
        long anuladas = 0;
        long unidades = 0;
        BigDecimal base = BigDecimal.ZERO;
        BigDecimal impuestos = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal descuentos = BigDecimal.ZERO;
        BigDecimal costo = BigDecimal.ZERO;
        boolean costoCompleto = true;
        Map<LocalDate, PuntoIngreso> serie = new TreeMap<>();
        for (LocalDate dia = periodo.desde(); !dia.isAfter(periodo.hasta()); dia = dia.plusDays(1)) {
            serie.put(dia, new PuntoIngreso(dia, 0, BigDecimal.ZERO));
        }
        Map<Long, Vendedor> vendedores = new LinkedHashMap<>();
        for (FilaVenta v : ventas) {
            if (v.estado() == EstadoVenta.ANULADA) {
                anuladas++;
                continue;
            }
            numero++;
            unidades += v.unidades();
            base = base.add(v.base());
            impuestos = impuestos.add(v.impuestos());
            total = total.add(v.total());
            descuentos = descuentos.add(v.descuentos());
            if (v.costo() == null) {
                costoCompleto = false;
            } else {
                costo = costo.add(v.costo());
            }
            LocalDate dia = v.vendidaEn().atZone(calendario.zona()).toLocalDate();
            serie.computeIfPresent(dia, (k, p) -> new PuntoIngreso(k, p.ventas() + 1, p.ingresoNeto().add(v.base())));
            vendedores.merge(v.vendedorId(), new Vendedor(v.vendedorId(), v.vendedor(), 1, v.base(), v.descuentos()),
                    (a, b) -> new Vendedor(a.usuarioId(), a.nombre(), a.ventas() + 1, a.ingresoNeto().add(b.ingresoNeto()),
                            a.descuentos().add(b.descuentos())));
        }
        BigDecimal devuelto = BigDecimal.ZERO;
        for (FilaDevolucion d : devoluciones) {
            devuelto = devuelto.add(d.base());
            impuestos = impuestos.subtract(d.impuestos());
            total = total.subtract(d.total());
            if (d.costo() == null) {
                costoCompleto = false;
            } else {
                costo = costo.subtract(d.costo());
            }
            LocalDate dia = d.registradaEn().atZone(calendario.zona()).toLocalDate();
            serie.computeIfPresent(dia, (k, p) -> new PuntoIngreso(k, p.ventas(), p.ingresoNeto().subtract(d.base())));
            vendedores.merge(d.vendedorId(), new Vendedor(d.vendedorId(), d.vendedor(), 0, d.base().negate(), BigDecimal.ZERO),
                    (a, b) -> new Vendedor(a.usuarioId(), a.nombre(), a.ventas(), a.ingresoNeto().add(b.ingresoNeto()),
                            a.descuentos()));
        }
        BigDecimal ingresoNeto = base.subtract(devuelto);
        Rentabilidad r = Rentabilidad.de(ingresoNeto, costoCompleto ? costo : null);

        List<FilaProducto> vendido = repository.vendidoPorProducto(periodo.inicio(), periodo.fin());
        List<ProductoVendido> productos = productos(vendido, periodo);
        BigDecimal ingresoSinCosto = vendido.stream().map(FilaProducto::ingresoSinCosto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Resumen resumen = new Resumen(numero, anuladas, devoluciones.size(), unidades, Dinero.redondear(base),
                Dinero.redondear(devuelto), r.ingreso(), Dinero.redondear(impuestos), Dinero.redondear(total),
                Dinero.redondear(descuentos),
                numero == 0 ? null : ingresoNeto.divide(BigDecimal.valueOf(numero), Dinero.ESCALA, Dinero.REDONDEO),
                r.costo(), r.utilidad(), r.margen(), r.recargo(), costoCompleto, Dinero.redondear(ingresoSinCosto),
                repository.contarLineasBajoCosto(periodo.inicio(), periodo.fin()));

        List<ProductoVendido> masVendidos = productos.stream().filter(p -> p.unidades() > 0)
                .sorted(Comparator.comparingLong(ProductoVendido::unidades).reversed()
                        .thenComparing(ProductoVendido::nombre)).limit(TOP).toList();
        List<ProductoVendido> mayorUtilidad = productos.stream().filter(p -> p.utilidad() != null)
                .sorted(Comparator.comparing(ProductoVendido::utilidad).reversed().thenComparing(ProductoVendido::nombre))
                .limit(TOP).toList();
        List<Vendedor> porVendedor = vendedores.values().stream()
                .map(v -> new Vendedor(v.usuarioId(), v.nombre(), v.ventas(), Dinero.redondear(v.ingresoNeto()),
                        Dinero.redondear(v.descuentos())))
                .sorted(Comparator.comparing(Vendedor::ingresoNeto).reversed()).toList();
        List<LineaBajoCosto> bajoCosto = repository.lineasBajoCosto(periodo.inicio(), periodo.fin(), MAX_BAJO_COSTO)
                .stream().map(f -> {
                    BigDecimal costoLinea = Dinero.redondear(f.costoUnitario().multiply(BigDecimal.valueOf(f.cantidad())));
                    return new LineaBajoCosto(f.ventaId(), VentaDTO.numeroVisible(f.numero()), f.vendidaEn(), f.sku(),
                            f.nombre(), f.cantidad(), f.base(), costoLinea, costoLinea.subtract(f.base()));
                }).toList();

        return new Reporte(new ReporteVentasDTO.Periodo(periodo.desde(), periodo.hasta(), calendario.zona().getId()),
                MetodoCosto.PROMEDIO_PONDERADO.name(), true, resumen, List.copyOf(serie.values()).stream()
                .map(p -> new PuntoIngreso(p.fecha(), p.ventas(), Dinero.redondear(p.ingresoNeto()))).toList(),
                masVendidos, mayorUtilidad, porVendedor, bajoCosto, bajoInventario());
    }

    /** Neto de devoluciones por producto; utilidad y margen solo si todas sus líneas tienen costo. */
    private List<ProductoVendido> productos(List<FilaProducto> vendido, CalendarioNegocio.Periodo periodo) {
        Map<Long, FilaProducto> netos = new LinkedHashMap<>();
        for (FilaProducto f : vendido) {
            netos.put(f.productoId(), f);
        }
        for (FilaProducto d : repository.devueltoPorProducto(periodo.inicio(), periodo.fin())) {
            netos.merge(d.productoId(), new FilaProducto(d.productoId(), d.sku(), d.nombre(), -d.unidades(),
                    d.ingreso().negate(), d.costo().negate(), d.lineasSinCosto(), d.ingresoSinCosto().negate()),
                    (a, b) -> new FilaProducto(a.productoId(), a.sku(), a.nombre(), a.unidades() + b.unidades(),
                            a.ingreso().add(b.ingreso()), a.costo().add(b.costo()), a.lineasSinCosto() + b.lineasSinCosto(),
                            a.ingresoSinCosto().add(b.ingresoSinCosto())));
        }
        List<ProductoVendido> productos = new ArrayList<>();
        for (FilaProducto f : netos.values()) {
            Rentabilidad r = Rentabilidad.de(f.ingreso(), f.lineasSinCosto() == 0 ? f.costo() : null);
            productos.add(new ProductoVendido(f.productoId(), f.sku(), f.nombre(), f.unidades(), r.ingreso(), r.costo(),
                    r.utilidad(), r.margen()));
        }
        return productos;
    }

    private List<ProductoBajoInventario> bajoInventario() {
        var spec = Filtros.deProductosActivos().and(Filtros.aLaVenta())
                .and(Filtros.agotadas().or(Filtros.criticas()));
        return existenciaRepository.findAll(spec, PageRequest.of(0, MAX_BAJO_INVENTARIO, Sort.by("cantidad", "id")))
                .map(ReporteVentasService::bajoInventario).getContent();
    }

    private static ProductoBajoInventario bajoInventario(Existencia e) {
        return new ProductoBajoInventario(e.getProductoId(), e.getProducto().getSku(), e.getProducto().getNombre(),
                e.getCantidad(), e.getProducto().getStockMinimo(),
                EstadoStock.de(e.getCantidad(), e.getProducto().getStockMinimo()));
    }

    /** Ventas del día (hoy, por defecto) de quien pregunta: cantidad, unidades y total cobrado. */
    @PreAuthorize(Permisos.MIEMBRO_EMPRESA)
    public ReporteVentasDTO.MisVentas misVentas(LocalDate dia) {
        TenantContextHolder.requerirEmpresa();
        LocalDate fecha = dia == null ? calendario.hoy() : dia;
        Object[] fila = repository.ventasDe(UsuarioActual.obtener().getId(), calendario.inicioDelDia(fecha),
                calendario.inicioDelDia(fecha.plusDays(1)));
        BigDecimal total = fila[1] instanceof BigDecimal b ? b : new BigDecimal(fila[1].toString());
        return new ReporteVentasDTO.MisVentas(fecha, ((Number) fila[0]).longValue(), ((Number) fila[2]).longValue(),
                Dinero.redondear(total));
    }
}
