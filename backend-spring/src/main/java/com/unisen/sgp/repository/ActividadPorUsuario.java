package com.unisen.sgp.repository;

import java.time.Instant;

/**
 * Operaciones que firmó cada usuario en una fuente (solicitudes, recepciones, ventas): la más
 * reciente de toda su historia y cuántas desde una fecha. Una fila por usuario con al menos una.
 */
public interface ActividadPorUsuario {

    Long getUsuarioId();

    Instant getUltima();

    Long getRecientes();
}
