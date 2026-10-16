package com.unisen.sgp.model.entity;

/**
 * Situación de las existencias de un producto frente a su stock mínimo. Una sola definición
 * para el inventario, la analítica y la búsqueda por escáner.
 */
public enum EstadoStock {
    /** Sin unidades. */
    AGOTADO,
    /** Con unidades, pero en el stock mínimo o por debajo. */
    CRITICO,
    /** Por encima del stock mínimo. */
    NORMAL;

    public static EstadoStock de(int cantidad, int stockMinimo) {
        if (cantidad <= 0) {
            return AGOTADO;
        }
        return cantidad <= stockMinimo ? CRITICO : NORMAL;
    }
}
