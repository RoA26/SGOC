package com.unisen.sgp.exception;

/**
 * Una operación de plataforma (p. ej. la analítica global) se pidió dentro de una empresa
 * ({@code X-Tenant-ID: <id>}): debe pedirse en el modo global explícito (400). Así nunca devuelve
 * en silencio los datos de una sola empresa como si fueran de todas.
 */
public class ModoGlobalRequeridoException extends RuntimeException {

    public ModoGlobalRequeridoException() {
        super("Las métricas globales solo se consultan en el modo global explícito (X-Tenant-ID: global).");
    }
}
