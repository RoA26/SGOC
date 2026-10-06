package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.security.UsuarioPrincipal;

/** Vista pública de un usuario: nunca incluye el hash de la contraseña. */
public record UsuarioResponse(Long id, String email, String nombre, Rol rol) {

    public static UsuarioResponse from(UsuarioPrincipal principal) {
        return new UsuarioResponse(principal.getId(), principal.getUsername(), principal.getNombre(), principal.getRol());
    }
}
