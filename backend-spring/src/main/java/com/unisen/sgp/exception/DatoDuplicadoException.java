package com.unisen.sgp.exception;

/**
 * Un valor que debe ser único ya existe (409). Se responde con {@code errors.<campo>} para
 * que el formulario marque el campo afectado.
 */
public class DatoDuplicadoException extends RuntimeException {

    private final String campo;

    public DatoDuplicadoException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public static DatoDuplicadoException username() {
        return new DatoDuplicadoException("username", "Ese nombre de usuario ya está en uso.");
    }

    public static DatoDuplicadoException email() {
        return new DatoDuplicadoException("email", "Ya existe un usuario con este correo.");
    }

    public String getCampo() {
        return campo;
    }
}
