package com.unisen.sgp.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.unisen.sgp.model.entity.ProgramacionEntrega;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Una versión de la programación de la entrega. {@code fechaEstimada} es una fecha local (sin
 * hora); la franja solo existe si se acordó: sin ella, la entrega es "todo el día".
 */
public record ProgramacionEntregaDTO(
        int version,
        LocalDate fechaEstimada,
        @JsonFormat(pattern = "HH:mm") LocalTime horaDesde,
        @JsonFormat(pattern = "HH:mm") LocalTime horaHasta,
        boolean confirmadaProveedor,
        String referenciaProveedor,
        String observaciones,
        String motivoCambio,
        UsuarioResumenDTO registradaPor,
        Instant registradaEn) {

    /** Debe invocarse dentro de la transacción. */
    public static ProgramacionEntregaDTO from(ProgramacionEntrega programacion) {
        if (programacion == null) {
            return null;
        }
        return new ProgramacionEntregaDTO(programacion.getVersion(), programacion.getFechaEstimada(),
                programacion.getHoraDesde(), programacion.getHoraHasta(), programacion.isConfirmadaProveedor(),
                programacion.getReferenciaProveedor(), programacion.getObservaciones(), programacion.getMotivoCambio(),
                UsuarioResumenDTO.from(programacion.getRegistradaPor()), programacion.getRegistradaEn());
    }
}
