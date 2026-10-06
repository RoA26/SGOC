package com.unisen.sgp.model.dto;

import java.util.Locale;

/** Normalización de texto de entrada aplicada en los constructores de los DTO. */
final class Textos {

    private Textos() {
    }

    /** Recorta espacios; conserva {@code null}. */
    static String limpiar(String valor) {
        return valor == null ? null : valor.strip();
    }

    /** Recorta espacios y convierte los textos vacíos en {@code null} (campos opcionales). */
    static String vacioANull(String valor) {
        String limpio = limpiar(valor);
        return limpio == null || limpio.isEmpty() ? null : limpio;
    }

    static String mayusculas(String valor) {
        String limpio = limpiar(valor);
        return limpio == null ? null : limpio.toUpperCase(Locale.ROOT);
    }

    static String minusculas(String valor) {
        String limpio = limpiar(valor);
        return limpio == null ? null : limpio.toLowerCase(Locale.ROOT);
    }
}
