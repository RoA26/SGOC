package com.unisen.sgp.security;

import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.EstadoUsuario;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Usuario autenticado tal como lo ve Spring Security.
 *
 * <p>Es una instantánea desacoplada de la entidad JPA: el contexto de seguridad nunca
 * retiene entidades gestionadas por Hibernate.
 */
public final class UsuarioPrincipal implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String username;
    private final String email;
    private final String nombre;
    private final Rol rol;
    /** Estado leído de la BD al cargar el usuario (en cada petición, no del JWT). */
    private final EstadoUsuario estado;
    /** Tenant del usuario; null para SUPER_ADMIN. */
    private final Long empresaId;
    private final boolean empresaActiva;
    private final List<GrantedAuthority> authorities;
    private String passwordHash;

    private UsuarioPrincipal(Long id, String username, String email, String passwordHash, String nombre, Rol rol,
                             EstadoUsuario estado, Long empresaId, boolean empresaActiva) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nombre = nombre;
        this.rol = rol;
        this.estado = estado;
        this.empresaId = empresaId;
        this.empresaActiva = empresaActiva;
        this.authorities = List.of(new SimpleGrantedAuthority(rol.authority()));
    }

    /** La empresa del usuario debe estar cargada (UsuarioRepository.findByUsername la trae). */
    public static UsuarioPrincipal from(Usuario usuario) {
        Empresa empresa = usuario.getEmpresa();
        return new UsuarioPrincipal(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getEmail(),
                usuario.getPasswordHash(),
                usuario.getNombre(),
                usuario.getRol(),
                usuario.getEstado(),
                empresa == null ? null : empresa.getId(),
                empresa == null || empresa.isActiva());
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    /** false si su empresa fue desactivada: ningún usuario de ella puede operar. */
    public boolean isEmpresaActiva() {
        return empresaActiva;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNombre() {
        return nombre;
    }

    public Rol getRol() {
        return rol;
    }

    public EstadoUsuario getEstado() {
        return estado;
    }

    /** Identidad de Spring Security y "sub" del JWT. */
    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /** Solo una cuenta ACTIVA de una empresa activa (ver {@link CuentaNoAutorizadaException#motivoDe}). */
    @Override
    public boolean isEnabled() {
        return estado.permiteAcceso() && empresaActiva;
    }

    /** Spring Security la invoca tras autenticar: el hash no sobrevive al login. */
    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    @Override
    public String toString() {
        return "UsuarioPrincipal{id=" + id + ", username='" + username + "', rol=" + rol + ", empresaId=" + empresaId
                + ", estado=" + estado + '}';
    }
}
