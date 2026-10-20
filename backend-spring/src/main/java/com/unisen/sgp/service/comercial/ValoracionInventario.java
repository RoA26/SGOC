package com.unisen.sgp.service.comercial;

import java.math.BigDecimal;

/**
 * Costo promedio ponderado de las existencias. <b>Método provisional</b>: el sistema no tiene
 * una política de valoración aprobada por contabilidad; FIFO es la alternativa documentada
 * (docs/modulo-comercial.md). Cada línea de venta guarda el costo y el método con que se
 * calculó, así que cambiar de método no altera ninguna venta histórica.
 *
 * <p>Reglas:
 * <ul>
 *   <li>Entrada con costo: sin unidades previas, el promedio es ese costo; con unidades de
 *       promedio conocido, (saldo × promedio + cantidad × costo) / (saldo + cantidad); con
 *       unidades de costo desconocido, sigue desconocido (no se mezcla lo que no se conoce).</li>
 *   <li>Entrada sin costo (ajuste): se valora al promedio vigente, que no cambia.</li>
 *   <li>Salidas: no cambian el promedio (salen a ese costo).</li>
 * </ul>
 * La migración V16 aplicó estas mismas reglas al kárdex anterior al módulo comercial.
 */
public final class ValoracionInventario {

    private ValoracionInventario() {
    }

    /**
     * @param saldoPrevio unidades antes de la entrada
     * @param promedio    promedio vigente (null = desconocido)
     * @param cantidad    unidades que entran (> 0)
     * @param costo       costo unitario de la entrada (null = sin costo)
     * @return el promedio tras la entrada (null = desconocido)
     */
    public static BigDecimal promedioTrasEntrada(int saldoPrevio, BigDecimal promedio, int cantidad, BigDecimal costo) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("Una entrada tiene cantidad positiva.");
        }
        if (costo == null) {
            return promedio;
        }
        if (saldoPrevio <= 0) {
            return Dinero.costo(costo);
        }
        if (promedio == null) {
            return null;
        }
        BigDecimal valor = promedio.multiply(BigDecimal.valueOf(saldoPrevio)).add(costo.multiply(BigDecimal.valueOf(cantidad)));
        return valor.divide(BigDecimal.valueOf((long) saldoPrevio + cantidad), Dinero.ESCALA_COSTO, Dinero.REDONDEO);
    }
}
