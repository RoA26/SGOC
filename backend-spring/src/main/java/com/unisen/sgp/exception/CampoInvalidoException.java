package com.unisen.sgp.exception;

/**
 * Un campo es sintácticamente válido pero no cumple una regla de negocio (p. ej. referencia
 * a un proveedor dado de baja). Se responde 400 con el mismo formato que Bean Validation.
 */
public class CampoInvalidoException extends RuntimeException {

    private final String campo;

    public CampoInvalidoException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
