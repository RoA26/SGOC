package com.unisen.sgp.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Evento del calendario operativo, derivado de los datos reales (no hay tabla de calendario).
 *
 * <p>Qué fecha representa cada tipo:
 * <ul>
 *   <li>ORDEN_EMITIDA: el instante de emisión (con hora).</li>
 *   <li>ENTREGA: la fecha estimada vigente (un día, sin hora). Con franja acordada lleva
 *       {@code horaInicio}/{@code horaFin}; sin ella es "todo el día": nunca se inventa una hora.</li>
 *   <li>RECEPCION: el instante real en que se registró la mercancía (con hora).</li>
 *   <li>SOLICITUD_PENDIENTE: el instante en que se creó la solicitud que espera aprobación.</li>
 * </ul>
 * {@code fecha} y las horas están en la zona del negocio; {@code instante} es el valor exacto
 * (UTC) cuando existe.
 */
public record EventoCalendarioDTO(
        String id,
        Tipo tipo,
        String estado,
        LocalDate fecha,
        boolean todoElDia,
        @JsonFormat(pattern = "HH:mm") LocalTime horaInicio,
        @JsonFormat(pattern = "HH:mm") LocalTime horaFin,
        Instant instante,
        String titulo,
        String detalle,
        Long ordenId,
        Long ordenNumero,
        String proveedor,
        Long solicitudId,
        Long recepcionId,
        boolean confirmada,
        boolean parcial,
        int incidencias) {

    public enum Tipo {
        ORDEN_EMITIDA,
        ENTREGA,
        RECEPCION,
        SOLICITUD_PENDIENTE
    }
}
