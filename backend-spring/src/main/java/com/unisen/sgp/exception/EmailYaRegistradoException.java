package com.unisen.sgp.exception;

/** Se intenta registrar un correo que ya pertenece a otro usuario. */
public class EmailYaRegistradoException extends RuntimeException {

    public EmailYaRegistradoException(String email) {
        super("Ya existe un usuario con el correo " + email + ".");
    }
}
