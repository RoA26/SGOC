package com.unisen.sgp.service.comercial;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Única definición de la precisión y el redondeo de los importes comerciales (pesos colombianos).
 *
 * <ul>
 *   <li>Importes (precios, descuentos, bases, impuestos, totales): 2 decimales, redondeo
 *       {@link RoundingMode#HALF_UP} (el "medio hacia arriba" habitual). Las columnas son
 *       NUMERIC(14, 2).</li>
 *   <li>Costos unitarios promedio: 4 decimales (un promedio rara vez es exacto en centavos).</li>
 *   <li>Porcentajes de margen, recargo y descuento: 2 decimales, HALF_UP.</li>
 * </ul>
 * Nunca se usa {@code float} ni {@code double} con dinero.
 */
public final class Dinero {

    public static final int ESCALA = 2;
    public static final int ESCALA_COSTO = 4;
    public static final int ESCALA_PORCENTAJE = 2;
    public static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    public static final BigDecimal CIEN = BigDecimal.valueOf(100);
    public static final BigDecimal CERO = BigDecimal.ZERO.setScale(ESCALA);
    /** Un centavo: la unidad mínima en la que se reparte el descuento general. */
    public static final BigDecimal CENTAVO = new BigDecimal("0.01");

    private Dinero() {
    }

    public static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(ESCALA, REDONDEO);
    }

    /** {@code valor × porcentaje / 100}, redondeado a centavos. */
    public static BigDecimal porcentajeDe(BigDecimal valor, BigDecimal porcentaje) {
        return redondear(valor.multiply(porcentaje).divide(CIEN, ESCALA + 6, REDONDEO));
    }

    /** {@code parte / total × 100} con 2 decimales; null si el total es cero (no está definido). */
    public static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        if (total == null || parte == null || total.signum() == 0) {
            return null;
        }
        return parte.multiply(CIEN).divide(total, ESCALA_PORCENTAJE, REDONDEO);
    }

    public static BigDecimal costo(BigDecimal valor) {
        return valor == null ? null : valor.setScale(ESCALA_COSTO, REDONDEO);
    }
}
