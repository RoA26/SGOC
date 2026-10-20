package com.unisen.sgp.exception;

/**
 * Regla comercial que exige una autorización que no se presentó o no es válida (descuento por
 * encima del límite, venta bajo el costo). Responde 403 sin {@code motivo}: la interfaz no cierra
 * la sesión, solo informa y permite pedir un código al administrador comercial.
 */
public class OperacionNoAutorizadaException extends RuntimeException {

    public OperacionNoAutorizadaException(String mensaje) {
        super(mensaje);
    }
}
