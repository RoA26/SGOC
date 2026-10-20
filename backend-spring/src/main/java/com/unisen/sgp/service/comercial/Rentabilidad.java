package com.unisen.sgp.service.comercial;

import java.math.BigDecimal;

/**
 * Indicadores de rentabilidad bruta, con las mismas fórmulas en el detalle de venta, la gestión
 * comercial y los reportes:
 *
 * <ul>
 *   <li><b>Utilidad bruta</b> = ingreso neto de venta (sin IVA, después de descuentos) − costo de
 *       lo vendido (costos históricos). No es utilidad neta: no descuenta gastos operativos que
 *       el sistema no registra.</li>
 *   <li><b>Margen bruto (%)</b> = utilidad bruta / ingreso neto × 100. Con ingreso cero no está
 *       definido (null).</li>
 *   <li><b>Recargo sobre el costo (%)</b> = utilidad bruta / costo × 100. Con costo cero o
 *       desconocido no está definido (null), nunca 0 %.</li>
 * </ul>
 * Con costo desconocido, la utilidad también lo es (null): desconocida no es cero.
 *
 * @param ingreso  ingreso neto (base sin IVA)
 * @param costo    costo de lo vendido; null si se desconoce
 * @param utilidad null si el costo se desconoce
 * @param margen   null si no está definido
 * @param recargo  null si no está definido
 */
public record Rentabilidad(BigDecimal ingreso, BigDecimal costo, BigDecimal utilidad, BigDecimal margen,
                           BigDecimal recargo) {

    public static Rentabilidad de(BigDecimal ingreso, BigDecimal costo) {
        BigDecimal ingresoRedondeado = Dinero.redondear(ingreso);
        if (costo == null) {
            return new Rentabilidad(ingresoRedondeado, null, null, null, null);
        }
        BigDecimal costoRedondeado = Dinero.redondear(costo);
        BigDecimal utilidad = ingresoRedondeado.subtract(costoRedondeado);
        return new Rentabilidad(ingresoRedondeado, costoRedondeado, utilidad,
                Dinero.porcentaje(utilidad, ingresoRedondeado), Dinero.porcentaje(utilidad, costoRedondeado));
    }

    /** Precio (sin IVA) con el que se obtendría el margen bruto objetivo sobre el costo: costo / (1 − margen). */
    public static BigDecimal precioSugerido(BigDecimal costoUnitario, BigDecimal margenObjetivo) {
        if (costoUnitario == null || margenObjetivo == null || margenObjetivo.compareTo(Dinero.CIEN) >= 0) {
            return null;
        }
        BigDecimal factor = Dinero.CIEN.subtract(margenObjetivo);
        return Dinero.redondear(costoUnitario.multiply(Dinero.CIEN).divide(factor, Dinero.ESCALA + 6, Dinero.REDONDEO));
    }

    public boolean perdida() {
        return utilidad != null && utilidad.signum() < 0;
    }
}
