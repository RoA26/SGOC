package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.EstadoUsuario;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import java.time.Instant;

/** Trabajador de la empresa tal como lo ve su gestor (sin datos de acceso). */
public record TrabajadorResponseDTO(Long id, String username, String email, String nombre, Rol rol,
                                    EstadoUsuario estado, Instant creadoEn) {

    public static TrabajadorResponseDTO from(Usuario usuario) {
        return new TrabajadorResponseDTO(usuario.getId(), usuario.getUsername(), usuario.getEmail(),
                usuario.getNombre(), usuario.getRol(), usuario.getEstado(), usuario.getCreadoEn());
    }
}
