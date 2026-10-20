package com.unisen.sgp.model.entity;

/** Cómo se obtuvo el costo histórico de una línea de venta. */
public enum MetodoCosto {
    /** Costo promedio ponderado de las existencias al vender (método provisional, ver docs). */
    PROMEDIO_PONDERADO,
    /** No había costo fiable: la rentabilidad de la línea es desconocida (no cero). */
    DESCONOCIDO
}
