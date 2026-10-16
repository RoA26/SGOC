package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.EstadoUsuario;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import java.time.Instant;

/**
 * Persona de una empresa tal como la ve quien la gestiona, sin datos de acceso: un trabajador para
 * su administrador, o cualquier usuario (también administradores) en la ficha de empresa del SUPER_ADMIN.
 */
public record TrabajadorResponseDTO(Long id, String username, String email, String nombre, Rol rol,
                                    EstadoUsuario estado, Instant creadoEn) {

    public static TrabajadorResponseDTO from(Usuario usuario) {
        return new TrabajadorResponseDTO(usuario.getId(), usuario.getUsername(), usuario.getEmail(),
                usuario.getNombre(), usuario.getRol(), usuario.getEstado(), usuario.getCreadoEn());
    }
}
