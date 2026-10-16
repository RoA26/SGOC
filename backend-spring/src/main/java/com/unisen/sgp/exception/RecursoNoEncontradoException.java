package com.unisen.sgp.exception;

/** El recurso solicitado no existe o fue dado de baja (404). */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String recurso, Long id) {
        super("No existe el " + recurso + " con id " + id + ".");
    }

    private RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }

    public static RecursoNoEncontradoException ordenCompra(Long id) {
        return new RecursoNoEncontradoException("No existe la orden de compra con id " + id + ".");
    }

    /** Búsqueda por código (escáner): ningún producto de la empresa lo tiene. */
    public static RecursoNoEncontradoException productoConCodigo(String codigo) {
        return new RecursoNoEncontradoException("Ningún producto tiene el código «" + codigo + "».");
    }

    /** También para las solicitudes ajenas: a un TRABAJADOR no se le revela que existen. */
    public static RecursoNoEncontradoException solicitud(Long id) {
        return new RecursoNoEncontradoException("No existe la solicitud con id " + id + ".");
    }
}
