package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.EstadoUsuario;
import java.time.Instant;

/**
 * Trabajador de la empresa en la vista de personal del administrador: identidad visible, estado
 * real de la cuenta y un resumen de la actividad que firmó. Sin correo ni datos de acceso.
 *
 * <p>{@code actividad} solo cuenta operaciones registradas (solicitudes creadas, recepciones
 * registradas y ventas realizadas, también las anuladas después). <strong>No es la última
 * conexión</strong>: el sistema no registra los inicios de sesión.
 */
public record PersonalDTO(Long id, String username, String nombre, EstadoUsuario estado, Instant creadoEn,
                          Actividad actividad) {

    /** Fuente de una operación atribuible a un trabajador. */
    public enum TipoOperacion {
        SOLICITUD,
        RECEPCION,
        VENTA
    }

    /** Operación más reciente de toda la historia del trabajador. */
    public record UltimaOperacion(TipoOperacion tipo, Instant en) {
    }

    /**
     * @param ultimaOperacion null (se omite en el JSON) si nunca registró ninguna operación
     * @param dias            ventana de los conteos (los últimos {@code dias} días hasta ahora)
     */
    public record Actividad(UltimaOperacion ultimaOperacion, int dias, long solicitudes, long recepciones,
                            long ventas) {
    }

    public static PersonalDTO from(TrabajadorResponseDTO trabajador, Actividad actividad) {
        return new PersonalDTO(trabajador.id(), trabajador.username(), trabajador.nombre(), trabajador.estado(),
                trabajador.creadoEn(), actividad);
    }
}
