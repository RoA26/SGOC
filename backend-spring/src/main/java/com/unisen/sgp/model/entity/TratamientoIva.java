package com.unisen.sgp.model.entity;

/**
 * Tratamiento del IVA de un producto. Se guarda aparte de la tarifa: una tarifa cero no convierte
 * un producto en exento ni en excluido. Datos de demostración: no son una clasificación tributaria
 * validada de productos reales.
 */
public enum TratamientoIva {
    /** Causa IVA a la tarifa indicada (p. ej. 19 % o 5 %). */
    GRAVADO,
    /** Gravado a tarifa 0 % (exento): no se cobra IVA, pero la operación está gravada. */
    EXENTO,
    /** No causa IVA (excluido): la operación no está sujeta al impuesto. */
    EXCLUIDO;

    public boolean cobraImpuesto() {
        return this == GRAVADO;
    }
}
