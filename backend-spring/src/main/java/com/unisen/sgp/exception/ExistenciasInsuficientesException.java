package com.unisen.sgp.exception;

import java.util.List;

/** 409 con el detalle de cada producto sin unidades suficientes (para marcarlo en el carrito). */
public class ExistenciasInsuficientesException extends ConflictoException {

    public record Faltante(Long productoId, String nombre, int solicitadas, int disponibles) {
    }

    private final transient List<Faltante> faltantes;

    public ExistenciasInsuficientesException(List<Faltante> faltantes) {
        super("Existencias insuficientes: " + String.join("; ", faltantes.stream()
                .map(f -> "«" + f.nombre() + "» tiene " + f.disponibles() + " y se piden " + f.solicitadas()).toList())
                + ".");
        this.faltantes = List.copyOf(faltantes);
    }

    public List<Faltante> getFaltantes() {
        return faltantes;
    }
}
