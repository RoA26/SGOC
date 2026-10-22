package com.unisen.sgp.exception;

import java.time.Duration;

/**
 * Se superó un límite de intentos (429 con {@code Retry-After}). El mensaje es el mismo exista o
 * no la cuenta o el código: no revela nada sobre ellos.
 */
public class DemasiadosIntentosException extends RuntimeException {

    private final transient Duration espera;

    public DemasiadosIntentosException(String mensaje, Duration espera) {
        super(mensaje);
        this.espera = espera;
    }

    /** Tiempo hasta que vuelve a haber cupo. */
    public Duration getEspera() {
        return espera;
    }

    /** Segundos enteros para {@code Retry-After}, redondeando hacia arriba (mínimo 1). */
    public long getSegundos() {
        return segundos(espera);
    }

    /** Segundos enteros de una espera, redondeando hacia arriba (mínimo 1). */
    public static long segundos(Duration espera) {
        return Math.max(1, espera.toSeconds() + (espera.toNanosPart() > 0 ? 1 : 0));
    }
}
