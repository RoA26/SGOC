package com.unisen.sgp.repository;

import com.unisen.sgp.model.dto.AnaliticaDTO.EmpresaConActividad;
import com.unisen.sgp.model.dto.AnaliticaDTO.OrdenesDeEmpresa;
import com.unisen.sgp.model.dto.AnaliticaDTO.ProductoEnAlerta;
import com.unisen.sgp.model.entity.EstadoOrdenCompra;
import com.unisen.sgp.model.entity.EstadoSolicitud;
import com.unisen.sgp.model.entity.EstadoStock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Consultas agregadas de la analítica (COUNT/SUM/GROUP BY en la BD; nunca se cargan filas para
 * contarlas en memoria). Cada método es una sola consulta.
 *
 * <p>Como el resto de repositorios, las entidades de empresa pasan por el filtro de
 * {@code @TenantId}: con una empresa en el contexto, solo cuentan sus datos; en el modo global
 * del SUPER_ADMIN (sin empresa), los de todas. {@link com.unisen.sgp.model.entity.Empresa} es
 * global.
 *
 * <p>Las consultas de la plataforma reciben {@code demo}: {@code false} solo cuenta las empresas
 * reales, {@code true} solo las de demostración y {@code null} todas (ver
 * {@link com.unisen.sgp.model.dto.AnaliticaDTO.AlcanceEmpresas}). Dentro de una empresa no se usa.
 */
@Repository
public class AnaliticaRepository {

    /** Hora UTC de un instante: idéntica en PostgreSQL y H2 (ver CalendarioNegocio). */
    private static final String HORA_UTC = "floor(extract(epoch from %s) / 3600)";

    @PersistenceContext
    private EntityManager em;

    // --- Plataforma ---

    /** [total, activas]. */
    public long[] conteoEmpresas(Boolean demo) {
        Object[] fila = conDemo(em.createQuery("""
                select count(e), coalesce(sum(case when e.activa = true then 1 else 0 end), 0)
                from Empresa e""" + donde("e", demo), Object[].class), demo).getSingleResult();
        return new long[] {numero(fila[0]), numero(fila[1])};
    }

    public List<EmpresaConActividad> empresasConMasOrdenes(Instant inicio, Instant fin, int limite, Boolean demo) {
        return conDemo(em.createQuery("""
                        select e.id, e.nombre, e.codigo, e.demo, count(distinct o.id),
                               coalesce(sum(case when o.estado <> :cancelada then d.cantidad * d.costoUnitario
                                                 else 0 end), 0)
                        from OrdenCompra o join o.empresa e join o.detalles d
                        where o.emitidaEn >= :inicio and o.emitidaEn < :fin""" + y("e", demo) + """

                        group by e.id, e.nombre, e.codigo, e.demo
                        order by count(distinct o.id) desc, e.nombre asc""", Object[].class), demo)
                .setParameter("cancelada", EstadoOrdenCompra.CANCELADA)
                .setParameter("inicio", inicio)
                .setParameter("fin", fin)
                .setMaxResults(limite)
                .getResultStream()
                .map(f -> new EmpresaConActividad((Long) f[0], (String) f[1], (String) f[2], (Boolean) f[3], numero(f[4]),
                        importe(f[5])))
                .toList();
    }

    /** Las empresas del alcance (paginadas) con sus órdenes emitidas en el período y las abiertas ahora. */
    public Page<OrdenesDeEmpresa> ordenesPorEmpresa(Instant inicio, Instant fin, Pageable pageable, Boolean demo) {
        List<OrdenesDeEmpresa> filas = conDemo(em.createQuery("""
                        select e.id, e.nombre, e.codigo, e.activa, e.demo,
                               count(case when o.emitidaEn >= :inicio and o.emitidaEn < :fin then o.id end),
                               count(case when o.estado in :abiertas then o.id end)
                        from Empresa e left join OrdenCompra o on o.empresaId = e.id""" + donde("e", demo) + """

                        group by e.id, e.nombre, e.codigo, e.activa, e.demo
                        order by count(case when o.emitidaEn >= :inicio and o.emitidaEn < :fin then o.id end) desc,
                                 e.nombre asc, e.id asc""", Object[].class), demo)
                .setParameter("inicio", inicio)
                .setParameter("fin", fin)
                .setParameter("abiertas", EstadoOrdenCompra.ABIERTAS)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultStream()
                .map(f -> new OrdenesDeEmpresa((Long) f[0], (String) f[1], (String) f[2], (Boolean) f[3], (Boolean) f[4],
                        numero(f[5]), numero(f[6])))
                .toList();
        long total = conDemo(em.createQuery("select count(e) from Empresa e" + donde("e", demo), Long.class), demo)
                .getSingleResult();
        return new PageImpl<>(filas, pageable, total);
    }

    // --- Órdenes de compra ---

    public Map<EstadoOrdenCompra, Long> ordenesPorEstado() {
        return ordenesPorEstado(null);
    }

    public Map<EstadoOrdenCompra, Long> ordenesPorEstado(Boolean demo) {
        Map<EstadoOrdenCompra, Long> conteo = ceros(EstadoOrdenCompra.class);
        conDemo(em.createQuery("select o.estado, count(o) from OrdenCompra o" + donde("o.empresa", demo)
                        + " group by o.estado", Object[].class), demo)
                .getResultStream()
                .forEach(f -> conteo.put((EstadoOrdenCompra) f[0], numero(f[1])));
        return conteo;
    }

    public long totalOrdenes() {
        return totalOrdenes(null);
    }

    public long totalOrdenes(Boolean demo) {
        return conDemo(em.createQuery("select count(o) from OrdenCompra o" + donde("o.empresa", demo), Long.class), demo)
                .getSingleResult();
    }

    public long ordenesEmitidas(Instant inicio, Instant fin) {
        return ordenesEmitidas(inicio, fin, null);
    }

    public long ordenesEmitidas(Instant inicio, Instant fin, Boolean demo) {
        return conDemo(em.createQuery("select count(o) from OrdenCompra o where o.emitidaEn >= :inicio and o.emitidaEn < :fin"
                        + y("o.empresa", demo), Long.class), demo)
                .setParameter("inicio", inicio)
                .setParameter("fin", fin)
                .getSingleResult();
    }

    /** Valor de las órdenes emitidas en el período que no se cancelaron. */
    public BigDecimal valorComprometido(Instant inicio, Instant fin) {
        Object valor = em.createQuery("""
                        select coalesce(sum(d.cantidad * d.costoUnitario), 0)
                        from OrdenCompra o join o.detalles d
                        where o.emitidaEn >= :inicio and o.emitidaEn < :fin and o.estado <> :cancelada""", Object.class)
                .setParameter("inicio", inicio)
                .setParameter("fin", fin)
                .setParameter("cancelada", EstadoOrdenCompra.CANCELADA)
                .getSingleResult();
        return importe(valor);
    }

    /** Órdenes emitidas por hora UTC (hora = segundos epoch / 3600). */
    public Map<Long, Long> ordenesEmitidasPorHora(Instant inicio, Instant fin, Boolean demo) {
        String hora = HORA_UTC.formatted("o.emitidaEn");
        return porHora(conDemo(em.createQuery("select " + hora + ", count(o) from OrdenCompra o "
                + "where o.emitidaEn >= :inicio and o.emitidaEn < :fin" + y("o.empresa", demo) + " group by " + hora,
                        Object[].class), demo)
                .setParameter("inicio", inicio)
                .setParameter("fin", fin)
                .getResultList());
    }

    // --- Recepciones ---

    /** [recepciones, unidades] y el valor recibido al costo de la orden, en el período. */
    public Object[] recepciones(Instant inicio, Instant fin) {
        Object[] fila = em.createQuery("""
                        select count(distinct r.id), coalesce(sum(dr.cantidad), 0),
                               coalesce(sum(dr.cantidad * dor.costoUnitario), 0)
                        from DetalleRecepcion dr join dr.recepcion r join dr.detalleOrden dor
                        where r.recibidaEn >= :inicio and r.recibidaEn < :fin""", Object[].class)
                .setParameter("inicio", inicio)
                .setParameter("fin", fin)
                .getSingleResult();
        return new Object[] {numero(fila[0]), numero(fila[1]), importe(fila[2])};
    }

    public long totalRecepciones() {
        return em.createQuery("select count(r) from Recepcion r", Long.class).getSingleResult();
    }

    public Map<Long, Long> recepcionesPorHora(Instant inicio, Instant fin) {
        String hora = HORA_UTC.formatted("r.recibidaEn");
        return porHora(em.createQuery("select " + hora + ", count(r) from Recepcion r "
                + "where r.recibidaEn >= :inicio and r.recibidaEn < :fin group by " + hora, Object[].class)
                .setParameter("inicio", inicio)
                .setParameter("fin", fin)
                .getResultList());
    }

    // --- Inventario y solicitudes ---

    /** [productos activos, disponibles (con unidades), agotados, críticos]. */
    public long[] estadoInventario() {
        Object[] fila = em.createQuery("""
                select count(x),
                       coalesce(sum(case when x.cantidad > 0 then 1 else 0 end), 0),
                       coalesce(sum(case when x.cantidad = 0 then 1 else 0 end), 0),
                       coalesce(sum(case when x.cantidad > 0 and x.cantidad <= p.stockMinimo then 1 else 0 end), 0)
                from Existencia x join x.producto p
                where p.activo = true""", Object[].class).getSingleResult();
        return new long[] {numero(fila[0]), numero(fila[1]), numero(fila[2]), numero(fila[3])};
    }

    /** Productos activos agotados o en su mínimo, los más urgentes primero. */
    public List<ProductoEnAlerta> productosEnAlerta(int limite) {
        return em.createQuery("""
                        select p.id, p.sku, p.nombre, x.cantidad, p.stockMinimo
                        from Existencia x join x.producto p
                        where p.activo = true and x.cantidad <= p.stockMinimo
                        order by x.cantidad asc, p.nombre asc, p.id asc""", Object[].class)
                .setMaxResults(limite)
                .getResultStream()
                .map(f -> {
                    int cantidad = (Integer) f[3];
                    int minimo = (Integer) f[4];
                    return new ProductoEnAlerta((Long) f[0], (String) f[1], (String) f[2], cantidad, minimo,
                            EstadoStock.de(cantidad, minimo));
                })
                .toList();
    }

    /** Solicitudes por estado actual; con {@code solicitanteId}, solo las de esa persona. */
    public Map<EstadoSolicitud, Long> solicitudesPorEstado(Long solicitanteId) {
        Map<EstadoSolicitud, Long> conteo = ceros(EstadoSolicitud.class);
        String filtro = solicitanteId == null ? "" : " where s.solicitante.id = :solicitante";
        var consulta = em.createQuery("select s.estado, count(s) from Solicitud s" + filtro + " group by s.estado",
                Object[].class);
        if (solicitanteId != null) {
            consulta.setParameter("solicitante", solicitanteId);
        }
        consulta.getResultStream().forEach(f -> conteo.put((EstadoSolicitud) f[0], numero(f[1])));
        return conteo;
    }

    /** {@code where} sobre la marca demo de la empresa {@code ruta}; vacío si {@code demo} es null (todas). */
    private static String donde(String ruta, Boolean demo) {
        return demo == null ? "" : " where " + ruta + ".demo = :demo";
    }

    /** Como {@link #donde}, para añadirlo a un {@code where} existente. */
    private static String y(String ruta, Boolean demo) {
        return demo == null ? "" : " and " + ruta + ".demo = :demo";
    }

    private static <T> TypedQuery<T> conDemo(TypedQuery<T> consulta, Boolean demo) {
        return demo == null ? consulta : consulta.setParameter("demo", demo);
    }

    private static Map<Long, Long> porHora(List<Object[]> filas) {
        Map<Long, Long> conteo = new HashMap<>();
        filas.forEach(f -> conteo.merge(numero(f[0]), numero(f[1]), Long::sum));
        return conteo;
    }

    private static <E extends Enum<E>> Map<E, Long> ceros(Class<E> tipo) {
        Map<E, Long> conteo = new EnumMap<>(tipo);
        for (E valor : tipo.getEnumConstants()) {
            conteo.put(valor, 0L);
        }
        return conteo;
    }

    /** Los agregados llegan como Long, Integer, BigDecimal o Double según la BD y la expresión. */
    private static long numero(Object valor) {
        return valor == null ? 0 : ((Number) valor).longValue();
    }

    private static BigDecimal importe(Object valor) {
        if (valor == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal importe = valor instanceof BigDecimal decimal ? decimal : new BigDecimal(valor.toString());
        return importe.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
