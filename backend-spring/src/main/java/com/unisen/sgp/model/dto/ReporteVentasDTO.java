package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.EstadoStock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Reporte comercial de un período (fechas locales de la zona del negocio, ambas incluidas).
 *
 * <p>Definiciones (docs/modulo-comercial.md):
 * <ul>
 *   <li>Ventas del período: confirmadas en el período que no están anuladas (una anulación, que
 *       solo se admite el mismo día, hace que la venta no cuente).</li>
 *   <li>Ingreso neto = base sin IVA de esas ventas − base de las devoluciones registradas en el
 *       período (una devolución cuenta el día en que se registra, no el de la venta).</li>
 *   <li>Costo = costo histórico de lo vendido − costo histórico de lo devuelto. Si alguna línea
 *       del período no tiene costo, el costo, la utilidad, el margen y el recargo son null
 *       ({@code costoCompleto = false}) y {@code ingresoSinCosto} dice cuánto ingreso lo causa.</li>
 *   <li>Utilidad bruta = ingreso neto − costo; margen = utilidad / ingreso; recargo = utilidad /
 *       costo (null si el divisor es 0).</li>
 *   <li>Descuentos: los concedidos en las ventas del período (de línea y generales, sin IVA).</li>
 * </ul>
 */
public final class ReporteVentasDTO {

    private ReporteVentasDTO() {
    }

    public record Periodo(LocalDate desde, LocalDate hasta, String zonaHoraria) {
    }

    public record Resumen(long numeroVentas, long ventasAnuladas, long numeroDevoluciones, long unidadesVendidas,
                          BigDecimal ingresoBruto, BigDecimal devoluciones, BigDecimal ingresoNeto,
                          BigDecimal impuestosNetos, BigDecimal totalCobradoNeto, BigDecimal descuentos,
                          BigDecimal ventaPromedio, BigDecimal costo, BigDecimal utilidadBruta, BigDecimal margenBruto,
                          BigDecimal recargo, boolean costoCompleto, BigDecimal ingresoSinCosto, long lineasBajoCosto) {
    }

    public record PuntoIngreso(LocalDate fecha, long ventas, BigDecimal ingresoNeto) {
    }

    /** Unidades e importes netos de devoluciones; utilidad y margen null si falta algún costo. */
    public record ProductoVendido(Long productoId, String sku, String nombre, long unidades, BigDecimal ingresoNeto,
                                  BigDecimal costo, BigDecimal utilidad, BigDecimal margen) {
    }

    public record Vendedor(Long usuarioId, String nombre, long ventas, BigDecimal ingresoNeto, BigDecimal descuentos) {
    }

    public record LineaBajoCosto(Long ventaId, String numeroVisible, Instant vendidaEn, String sku, String nombre,
                                 int cantidad, BigDecimal ingreso, BigDecimal costo, BigDecimal perdida) {
    }

    public record ProductoBajoInventario(Long productoId, String sku, String nombre, int existencias, int stockMinimo,
                                         EstadoStock estado) {
    }

    public record Reporte(Periodo periodo, String metodoValoracion, boolean valoracionProvisional, Resumen resumen,
                          List<PuntoIngreso> serie, List<ProductoVendido> masVendidos,
                          List<ProductoVendido> mayorUtilidad, List<Vendedor> porVendedor,
                          List<LineaBajoCosto> ventasBajoCosto, List<ProductoBajoInventario> bajoInventario) {
    }

    /** Ventas del día de quien pregunta (sin costos ni rentabilidad). */
    public record MisVentas(LocalDate dia, long ventas, long unidades, BigDecimal total) {
    }
}
