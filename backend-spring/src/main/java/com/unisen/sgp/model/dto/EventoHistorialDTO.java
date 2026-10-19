package com.unisen.sgp.model.dto;

import java.time.Instant;
import java.util.List;

/**
 * Un hecho de la vida de una orden, para reconstruir su secuencia. Se deriva de los datos que
 * ya existen (orden, solicitud, documentos, programaciones, recepciones y kárdex): no hay una
 * tabla de eventos aparte. Solo uno de los bloques opcionales acompaña a cada tipo.
 */
public record EventoHistorialDTO(
        Tipo tipo,
        Instant instante,
        UsuarioResumenDTO usuario,
        String descripcion,
        DocumentoOrdenDTO documento,
        ProgramacionEntregaDTO programacion,
        RecepcionResponseDTO recepcion,
        List<Movimiento> movimientos) {

    public enum Tipo {
        SOLICITUD_CREADA,
        SOLICITUD_APROBADA,
        ORDEN_CREADA,
        ORDEN_EMITIDA,
        DOCUMENTO_GENERADO,
        ENTREGA_PROGRAMADA,
        ENTREGA_REPROGRAMADA,
        RECEPCION_PARCIAL,
        RECEPCION_FINAL,
        /** La entrega no trajo nada aceptable: solo incidencias. */
        RECEPCION_CON_INCIDENCIAS,
        ORDEN_CANCELADA
    }

    /** Movimiento de kárdex causado por una recepción. */
    public record Movimiento(Long id, Long detalleRecepcionId, OrdenCompraResponseDTO.Producto producto, int cantidad,
                             int saldoResultante) {
    }

    public static EventoHistorialDTO simple(Tipo tipo, Instant instante, UsuarioResumenDTO usuario, String descripcion) {
        return new EventoHistorialDTO(tipo, instante, usuario, descripcion, null, null, null, null);
    }
}
