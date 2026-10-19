package com.unisen.sgp.model.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Eventos de [desde, hasta] en la zona del negocio, ordenados por día y hora (los de todo el
 * día primero). {@code truncado}: había más eventos de los que se devuelven.
 */
public record CalendarioDTO(LocalDate desde, LocalDate hasta, LocalDate hoy, String zonaHoraria,
                            List<EventoCalendarioDTO> eventos, boolean truncado) {
}
