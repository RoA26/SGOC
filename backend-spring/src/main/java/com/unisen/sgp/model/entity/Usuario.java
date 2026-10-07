package com.unisen.sgp.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "usuarios")
public class Usuario {

    public static final int USERNAME_MAX_LENGTH = 50;
    public static final int EMAIL_MAX_LENGTH = 320;
    public static final int NOMBRE_MAX_LENGTH = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador de inicio de sesión. Se almacena normalizado en minúsculas. */
    @Column(nullable = false, unique = true, length = USERNAME_MAX_LENGTH)
    private String username;

    @Column(nullable = false, unique = true, length = EMAIL_MAX_LENGTH)
    private String email;

    /** Hash BCrypt. Nunca se almacena ni se expone la contraseña en claro. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = NOMBRE_MAX_LENGTH)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Rol rol;

    @Column(nullable = false)
    private boolean activo = true;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Requerido por JPA. */
    protected Usuario() {
    }

    public Usuario(String username, String email, String passwordHash, String nombre, Rol rol) {
        // Asignación directa: invocar setters sobrescribibles desde el constructor es inseguro.
        this.username = normalizarUsername(Objects.requireNonNull(username, "username"));
        this.email = normalizarEmail(Objects.requireNonNull(email, "email"));
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.nombre = Objects.requireNonNull(nombre, "nombre").strip();
        this.rol = Objects.requireNonNull(rol, "rol");
    }

    /** Normaliza un nombre de usuario: el login no distingue mayúsculas ni espacios accidentales. */
    public static String normalizarUsername(String username) {
        return username == null ? null : username.strip().toLowerCase(Locale.ROOT);
    }

    /** Normaliza un correo para almacenarlo y buscarlo de forma consistente. */
    public static String normalizarEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = normalizarEmail(Objects.requireNonNull(email, "email"));
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = Objects.requireNonNull(nombre, "nombre").strip();
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = Objects.requireNonNull(rol, "rol");
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    // Igualdad por identificador, segura para entidades JPA (incluidos proxies de Hibernate).
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Usuario that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Usuario.class.hashCode();
    }

    @Override
    public String toString() {
        return "Usuario{id=" + id + ", username='" + username + "', rol=" + rol + ", activo=" + activo + '}';
    }
}
