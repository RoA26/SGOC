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
    private final String nombres;
    private final String apellidos;
    private final Rol rol;
    /** Estado leído de la BD al cargar el usuario (en cada petición, no del JWT). */
    private final EstadoUsuario estado;
    /** Tenant del usuario; null para SUPER_ADMIN. */
    private final Long empresaId;
    private final boolean empresaActiva;
    /** Nombre comercial y código empresarial de su empresa (null para SUPER_ADMIN). */
    private final String empresaNombre;
    private final String empresaCodigo;
    private final List<GrantedAuthority> authorities;
    private String passwordHash;

    private UsuarioPrincipal(Usuario usuario, Empresa empresa) {
        this.id = usuario.getId();
        this.username = usuario.getUsername();
        this.email = usuario.getEmail();
        this.passwordHash = usuario.getPasswordHash();
        this.nombre = usuario.getNombre();
        this.nombres = usuario.getNombres();
        this.apellidos = usuario.getApellidos();
        this.rol = usuario.getRol();
        this.estado = usuario.getEstado();
        this.empresaId = empresa == null ? null : empresa.getId();
        this.empresaActiva = empresa == null || empresa.isActiva();
        this.empresaNombre = empresa == null ? null : empresa.getNombre();
        this.empresaCodigo = empresa == null ? null : empresa.getCodigo();
        this.authorities = List.of(new SimpleGrantedAuthority(this.rol.authority()));
    }

    /** La empresa del usuario debe estar cargada (UsuarioRepository.findByUsername la trae). */
    public static UsuarioPrincipal from(Usuario usuario) {
        return new UsuarioPrincipal(usuario, usuario.getEmpresa());
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

    public String getNombres() {
        return nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public String getEmpresaNombre() {
        return empresaNombre;
    }

    public String getEmpresaCodigo() {
        return empresaCodigo;
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
