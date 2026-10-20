package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.EstadoVenta;
import com.unisen.sgp.model.entity.TipoDevolucion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Consultas de los reportes de ventas. Todas pasan por el filtro de {@code @TenantId}: solo ven
 * la empresa del contexto. Cada método es una sola consulta acotada al período.
 */
@Repository
public class ReporteVentasRepository {

    @PersistenceContext
    private EntityManager em;

    /** Cabecera de una venta del período (incluidas las anuladas, para contarlas aparte). */
    public record FilaVenta(Instant vendidaEn, EstadoVenta estado, BigDecimal base, BigDecimal impuestos, BigDecimal total,
                            BigDecimal descuentos, BigDecimal costo, Long vendedorId, String vendedor, long unidades) {
    }

    /** Devolución (no anulación) registrada en el período, con el vendedor de la venta original. */
    public record FilaDevolucion(Instant registradaEn, BigDecimal base, BigDecimal impuestos, BigDecimal total,
                                 BigDecimal costo, Long vendedorId, String vendedor) {
    }

    /** Agregado por producto: unidades, ingreso, costo de las líneas con costo y líneas sin costo. */
    public record FilaProducto(Long productoId, String sku, String nombre, long unidades, BigDecimal ingreso,
                               BigDecimal costo, long lineasSinCosto, BigDecimal ingresoSinCosto) {
    }

    public record FilaBajoCosto(Long ventaId, long numero, Instant vendidaEn, String sku, String nombre, int cantidad,
                                BigDecimal base, BigDecimal costoUnitario) {
    }

    public List<FilaVenta> ventas(Instant inicio, Instant fin) {
        return em.createQuery("""
                        select v.vendidaEn, v.estado, v.baseGravable, v.impuestos, v.total,
                               v.descuentoLineas + v.descuentoGeneral, v.costoTotal, u.id, u.nombre,
                               (select coalesce(sum(d.cantidad), 0) from DetalleVenta d where d.venta = v)
                        from Venta v join v.vendidaPor u
                        where v.vendidaEn >= :inicio and v.vendidaEn < :fin
                        order by v.vendidaEn""", Object[].class)
                .setParameter("inicio", inicio)
                .setParameter("fin", fin)
                .getResultStream()
                .map(f -> new FilaVenta((Instant) f[0], (EstadoVenta) f[1], (BigDecimal) f[2], (BigDecimal) f[3],
                        (BigDecimal) f[4], (BigDecimal) f[5], (BigDecimal) f[6], (Long) f[7], (String) f[8],
                        ((Number) f[9]).longValue()))
                .toList();
    }

    public List<FilaDevolucion> devoluciones(Instant inicio, Instant fin) {
        return em.createQuery("""
                        select d.registradaEn, d.baseGravable, d.impuestos, d.total, d.costoTotal, u.id, u.nombre
                        from DevolucionVenta d join d.venta v join v.vendidaPor u
                        where d.tipo = :tipo and d.registradaEn >= :inicio and d.registradaEn < :fin""", Object[].class)
                .setParameter("tipo", TipoDevolucion.DEVOLUCION)
                .setParameter("inicio", inicio)
                .setParameter("fin", fin)
                .getResultStream()
                .map(f -> new FilaDevolucion((Instant) f[0], (BigDecimal) f[1], (BigDecimal) f[2], (BigDecimal) f[3],
                        (BigDecimal) f[4], (Long) f[5], (String) f[6]))
                .toList();
    }

    /** Líneas de las ventas no anuladas del período, agregadas por producto. */
    public List<FilaProducto> vendidoPorProducto(Instant inicio, Instant fin) {
        return em.createQuery("""
                        select p.id, p.sku, p.nombre, sum(d.cantidad), sum(d.baseGravable),
                               sum(case when d.costoUnitario is not null then d.costoUnitario * d.cantidad else 0 end),
                               sum(case when d.costoUnitario is null then 1 else 0 end),
                               sum(case when d.costoUnitario is null then d.baseGravable else 0 end)
                        from DetalleVenta d join d.venta v join d.producto p
                        where v.estado <> :anulada and v.vendidaEn >= :inicio and v.vendidaEn < :fin
                        group by p.id, p.sku, p.nombre""", Object[].class)
                .setParameter("anulada", EstadoVenta.ANULADA)
                .setParameter("inicio", inicio)
                .setParameter("fin", fin)
                .getResultStream()
                .map(ReporteVentasRepository::filaProducto)
                .toList();
    }

    /** Líneas devueltas (devoluciones, no anulaciones) registradas en el período, por producto. */
    public List<FilaProducto> devueltoPorProducto(Instant inicio, Instant fin) {
        return em.createQuery("""
                        select p.id, p.sku, p.nombre, sum(dd.cantidad), sum(dd.baseGravable),
                               sum(case when dd.costoUnitario is not null then dd.costoUnitario * dd.cantidad else 0 end),
                               sum(case when dd.costoUnitario is null then 1 else 0 end),
                               sum(case when dd.costoUnitario is null then dd.baseGravable else 0 end)
                        from DetalleDevolucionVenta dd join dd.devolucion dv join dd.detalleVenta d join d.producto p
                        where dv.tipo = :tipo and dv.registradaEn >= :inicio and dv.registradaEn < :fin
                        group by p.id, p.sku, p.nombre""", Object[].class)
                .setParameter("tipo", TipoDevolucion.DEVOLUCION)
                .setParameter("inicio", inicio)
                .setParameter("fin", fin)
                .getResultStream()
                .map(ReporteVentasRepository::filaProducto)
                .toList();
    }

    public List<FilaBajoCosto> lineasBajoCosto(Instant inicio, Instant fin, int limite) {
        return em.createQuery("""
                        select v.id, v.numero, v.vendidaEn, d.sku, d.nombre, d.cantidad, d.baseGravable, d.costoUnitario
                        from DetalleVenta d join d.venta v
                        where d.bajoCosto = true and v.estado <> :anulada
                          and v.vendidaEn >= :inicio and v.vendidaEn < :fin
                        order by v.vendidaEn desc, d.linea asc""", Object[].class)
                .setParameter("anulada", EstadoVenta.ANULADA)
                .setParameter("inicio", inicio)
                .setParameter("fin", fin)
                .setMaxResults(limite)
                .getResultStream()
                .map(f -> new FilaBajoCosto((Long) f[0], ((Number) f[1]).longValue(), (Instant) f[2], (String) f[3],
                        (String) f[4], ((Number) f[5]).intValue(), (BigDecimal) f[6], (BigDecimal) f[7]))
                .toList();
    }

    public long contarLineasBajoCosto(Instant inicio, Instant fin) {
        return em.createQuery("""
                        select count(d) from DetalleVenta d join d.venta v
                        where d.bajoCosto = true and v.estado <> :anulada
                          and v.vendidaEn >= :inicio and v.vendidaEn < :fin""", Long.class)
                .setParameter("anulada", EstadoVenta.ANULADA)
                .setParameter("inicio", inicio)
                .setParameter("fin", fin)
                .getSingleResult();
    }

    /** [ventas, total cobrado, unidades] de un vendedor en el intervalo (sin anuladas). */
    public Object[] ventasDe(Long usuarioId, Instant inicio, Instant fin) {
        Object[] cabeceras = em.createQuery("""
                        select count(v), coalesce(sum(v.total), 0)
                        from Venta v
                        where v.vendidaPor.id = :usuario and v.estado <> :anulada
                          and v.vendidaEn >= :inicio and v.vendidaEn < :fin""", Object[].class)
                .setParameter("usuario", usuarioId)
                .setParameter("anulada", EstadoVenta.ANULADA)
                .setParameter("inicio", inicio)
                .setParameter("fin", fin)
                .getSingleResult();
        Long unidades = em.createQuery("""
                        select coalesce(sum(d.cantidad), 0) from DetalleVenta d join d.venta v
                        where v.vendidaPor.id = :usuario and v.estado <> :anulada
                          and v.vendidaEn >= :inicio and v.vendidaEn < :fin""", Long.class)
                .setParameter("usuario", usuarioId)
                .setParameter("anulada", EstadoVenta.ANULADA)
                .setParameter("inicio", inicio)
                .setParameter("fin", fin)
                .getSingleResult();
        return new Object[] {cabeceras[0], cabeceras[1], unidades};
    }

    private static FilaProducto filaProducto(Object[] f) {
        return new FilaProducto((Long) f[0], (String) f[1], (String) f[2], ((Number) f[3]).longValue(),
                (BigDecimal) f[4], decimal(f[5]), ((Number) f[6]).longValue(), decimal(f[7]));
    }

    private static BigDecimal decimal(Object valor) {
        return valor == null ? BigDecimal.ZERO : valor instanceof BigDecimal b ? b : new BigDecimal(valor.toString());
    }
}
