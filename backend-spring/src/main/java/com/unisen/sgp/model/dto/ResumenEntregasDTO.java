package com.unisen.sgp.model.dto;

/** Cifras de las entregas abiertas de la empresa, para la vista de supervisión. */
public record ResumenEntregasDTO(long pendientes, long paraHoy, long retrasadas, long sinProgramar, long parciales,
                                 long conIncidencias, long requierenIntervencion) {
}
