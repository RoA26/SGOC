package com.unisen.sgp.exception;

/** El recurso solicitado no existe o fue dado de baja (404). */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String recurso, Long id) {
        super("No existe el " + recurso + " con id " + id + ".");
    }
}
