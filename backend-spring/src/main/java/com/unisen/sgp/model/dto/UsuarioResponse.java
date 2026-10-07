package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.security.UsuarioPrincipal;

/** Vista pública de un usuario: nunca incluye el hash de la contraseña. */
public record UsuarioResponse(Long id, String username, String email, String nombre, Rol rol) {

    public static UsuarioResponse from(UsuarioPrincipal principal) {
        return new UsuarioResponse(principal.getId(), principal.getUsername(), principal.getEmail(),
                principal.getNombre(), principal.getRol());
    }

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getUsername(), usuario.getEmail(),
                usuario.getNombre(), usuario.getRol());
    }
}
