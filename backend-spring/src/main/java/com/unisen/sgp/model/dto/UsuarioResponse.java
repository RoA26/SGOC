package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.EstadoUsuario;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.security.UsuarioPrincipal;

/**
 * Vista pública de un usuario (login, perfil y altas): nunca incluye el hash de la contraseña.
 *
 * <ul>
 *   <li>{@code nombre}: nombre visible. {@code nombres} y {@code apellidos}: por separado; nulos
 *       en cuentas anteriores a V9 que aún no los completaron ({@code datosPersonalesCompletos}
 *       = false).</li>
 *   <li>{@code empresaId}, {@code empresaNombre} y {@code empresaCodigo}: su empresa, leída de la
 *       BD en cada petición (un cambio de nombre comercial se ve en el siguiente
 *       {@code GET /api/auth/me}). Se omiten para el SUPER_ADMIN, que no pertenece a ninguna.</li>
 *   <li>{@code estado}: si la cuenta tiene acceso (ACTIVO) o espera la aprobación (PENDIENTE).</li>
 * </ul>
 */
public record UsuarioResponse(Long id, String username, String email, String nombre, String nombres,
                              String apellidos, boolean datosPersonalesCompletos, Rol rol, Long empresaId,
                              String empresaNombre, String empresaCodigo, EstadoUsuario estado) {

    public static UsuarioResponse from(UsuarioPrincipal principal) {
        return new UsuarioResponse(principal.getId(), principal.getUsername(), principal.getEmail(),
                principal.getNombre(), principal.getNombres(), principal.getApellidos(),
                principal.getNombres() != null && principal.getApellidos() != null, principal.getRol(),
                principal.getEmpresaId(), principal.getEmpresaNombre(), principal.getEmpresaCodigo(),
                principal.getEstado());
    }

    /** Debe llamarse dentro de una transacción: lee la empresa (LAZY) del usuario. */
    public static UsuarioResponse from(Usuario usuario) {
        Empresa empresa = usuario.getEmpresa();
        return new UsuarioResponse(usuario.getId(), usuario.getUsername(), usuario.getEmail(), usuario.getNombre(),
                usuario.getNombres(), usuario.getApellidos(), usuario.tieneDatosPersonalesCompletos(),
                usuario.getRol(), usuario.getEmpresaId(), empresa == null ? null : empresa.getNombre(),
                empresa == null ? null : empresa.getCodigo(), usuario.getEstado());
    }
}
