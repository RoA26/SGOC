package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.CodigoInvitacion;
import com.unisen.sgp.model.entity.EstadoInvitacion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Invitación en el listado del administrador. Nunca incluye el código completo (es una
 * credencial que solo se devuelve al generarlo): {@code codigoParcial} muestra sus 4 últimos
 * caracteres para reconocerlo.
 */
public record InvitacionResumenDTO(
        Long id,
        @Schema(example = "••••-••••-••••-T3VW") String codigoParcial,
        EstadoInvitacion estado,
        Instant creadoEn,
        Instant fechaExpiracion,
        UsuarioResumenDTO creador,
        UsuarioResumenDTO usadoPor,
        Instant fechaUso,
        UsuarioResumenDTO revocadoPor,
        Instant fechaRevocacion) {

    public static InvitacionResumenDTO from(CodigoInvitacion invitacion, Instant ahora) {
        return new InvitacionResumenDTO(invitacion.getId(), invitacion.getCodigoParcial(), invitacion.estado(ahora),
                invitacion.getCreadoEn(), invitacion.getFechaExpiracion(),
                UsuarioResumenDTO.from(invitacion.getCreador()), UsuarioResumenDTO.from(invitacion.getUsadoPor()),
                invitacion.getFechaUso(), UsuarioResumenDTO.from(invitacion.getRevocadoPor()),
                invitacion.getFechaRevocacion());
    }
}
