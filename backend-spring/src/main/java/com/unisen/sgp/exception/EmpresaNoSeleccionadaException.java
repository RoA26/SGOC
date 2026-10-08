package com.unisen.sgp.exception;

/**
 * La operación trabaja con datos de una empresa y no hay ninguna en el contexto: un
 * SUPER_ADMIN en modo global debe elegirla con la cabecera {@code X-Tenant-ID} (400).
 */
public class EmpresaNoSeleccionadaException extends RuntimeException {

    public EmpresaNoSeleccionadaException() {
        super("Esta operación trabaja con los datos de una empresa: indícala con la cabecera X-Tenant-ID.");
    }
}
