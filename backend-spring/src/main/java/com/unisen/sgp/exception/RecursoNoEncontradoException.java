package com.unisen.sgp.exception;

/** El recurso solicitado no existe o fue dado de baja (404). */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String recurso, Long id) {
        super("No existe el " + recurso + " con id " + id + ".");
    }

    private RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }

    /** También para las solicitudes ajenas: a un USUARIO no se le revela que existen. */
    public static RecursoNoEncontradoException solicitud(Long id) {
        return new RecursoNoEncontradoException("No existe la solicitud con id " + id + ".");
    }
}
