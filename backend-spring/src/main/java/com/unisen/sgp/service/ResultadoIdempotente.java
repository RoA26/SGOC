package com.unisen.sgp.service;

/**
 * Resultado de una operación con clave de idempotencia: {@code nuevo = false} cuando la clave ya
 * se había usado con los mismos datos y se devuelve lo registrado entonces (el controlador
 * responde 200 en vez de 201).
 */
public record ResultadoIdempotente<T>(T valor, boolean nuevo) {
}
