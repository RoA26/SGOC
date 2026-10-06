package com.unisen.sgp.exception;

/** La operación choca con el estado actual de los datos (409). */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
