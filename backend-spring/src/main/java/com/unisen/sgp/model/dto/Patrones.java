package com.unisen.sgp.model.dto;

/**
 * Expresiones regulares de validación compartidas por los DTO.
 * El frontend replica exactamente estas reglas en sus esquemas Zod (src/schemas).
 */
public final class Patrones {

    /** 5 a 15 dígitos y, opcionalmente, dígito de verificación: 900123456-7. */
    public static final String NIT = "^\\d{5,15}(-[\\dK])?$";

    /** 7 a 20 caracteres: dígitos, espacios, +, - y paréntesis. */
    public static final String TELEFONO = "^[+(\\d][\\d ()+-]{6,19}$";

    /** 3 a 40 caracteres: letras, números, punto, guion y guion bajo. */
    public static final String SKU = "^[A-Z0-9._-]{3,40}$";

    /**
     * 3 a 50 caracteres en minúsculas: letras, números, punto, guion y guion bajo; empieza y
     * termina con letra o número. Se valida tras normalizar a minúsculas.
     */
    public static final String USERNAME = "^[a-z0-9](?:[a-z0-9._-]{1,48})[a-z0-9]$";

    /** Exige dominio con punto (Hibernate Validator aceptaría "a@b"). */
    public static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private Patrones() {
    }
}
