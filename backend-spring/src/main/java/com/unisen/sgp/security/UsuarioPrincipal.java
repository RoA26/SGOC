package com.unisen.sgp.security;

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
    private final boolean activo;
    private final List<GrantedAuthority> authorities;
    private String passwordHash;

    private UsuarioPrincipal(Long id, String username, String email, String passwordHash, String nombre, Rol rol,
                             boolean activo) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nombre = nombre;
        this.rol = rol;
        this.activo = activo;
        this.authorities = List.of(new SimpleGrantedAuthority(rol.authority()));
    }

    public static UsuarioPrincipal from(Usuario usuario) {
        return new UsuarioPrincipal(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getEmail(),
                usuario.getPasswordHash(),
                usuario.getNombre(),
                usuario.getRol(),
                usuario.isActivo());
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

    @Override
    public boolean isEnabled() {
        return activo;
    }

    /** Spring Security la invoca tras autenticar: el hash no sobrevive al login. */
    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    @Override
    public String toString() {
        return "UsuarioPrincipal{id=" + id + ", username='" + username + "', rol=" + rol + ", activo=" + activo + '}';
    }
}
