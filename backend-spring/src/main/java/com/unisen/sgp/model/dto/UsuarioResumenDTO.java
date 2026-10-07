package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.Usuario;

/** Datos mínimos de un usuario embebidos en otras respuestas (solicitante, revisor). */
public record UsuarioResumenDTO(Long id, String username, String nombre) {

    public static UsuarioResumenDTO from(Usuario usuario) {
        return usuario == null ? null : new UsuarioResumenDTO(usuario.getId(), usuario.getUsername(), usuario.getNombre());
    }
}
