package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.EstadoUsuario;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.security.UsuarioPrincipal;

/**
 * Vista pública de un usuario: nunca incluye el hash de la contraseña. {@code empresaId} se
 * omite para el SUPER_ADMIN, que no pertenece a ninguna empresa. {@code estado} indica si la
 * cuenta tiene acceso (ACTIVO) o espera la aprobación de un gestor (PENDIENTE).
 */
public record UsuarioResponse(Long id, String username, String email, String nombre, Rol rol, Long empresaId,
                              EstadoUsuario estado) {

    public static UsuarioResponse from(UsuarioPrincipal principal) {
        return new UsuarioResponse(principal.getId(), principal.getUsername(), principal.getEmail(),
                principal.getNombre(), principal.getRol(), principal.getEmpresaId(), principal.getEstado());
    }

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getUsername(), usuario.getEmail(),
                usuario.getNombre(), usuario.getRol(), usuario.getEmpresaId(), usuario.getEstado());
    }
}
