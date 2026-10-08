package com.unisen.sgp.model.entity;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.exception.ConflictoException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Identidad de acceso. No es una entidad con {@code @TenantId}: el login y la validación del
 * JWT ocurren antes de conocer la empresa, y el SUPER_ADMIN no tiene ninguna. La empresa del
 * usuario es la que fija su tenant en cada petición.
 */
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

    /**
     * Fuente de verdad de la autorización (ver {@link EstadoUsuario}): el filtro JWT la lee de
     * la BD en cada petición, así que un cambio surte efecto aunque el token siga vigente.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoUsuario estado = EstadoUsuario.PENDIENTE;

    /**
     * Reflejo de {@code estado == ACTIVO}, conservado del esquema anterior al Hito 2
     * ({@code ck_usuarios_estado_activo} impide que diverjan). Solo lo escribe {@link #fijarEstado}.
     */
    @Column(nullable = false)
    private boolean activo = false;

    /** Empresa a la que pertenece; null solo para SUPER_ADMIN (ck_usuarios_empresa). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Requerido por JPA. */
    protected Usuario() {
    }

    /** Cuenta sin acceso hasta que un gestor la apruebe (estado PENDIENTE). */
    public Usuario(String username, String email, String passwordHash, String nombre, Rol rol, Empresa empresa) {
        this(username, email, passwordHash, nombre, rol, empresa, EstadoUsuario.PENDIENTE);
    }

    /**
     * @param empresa obligatoria para GERENTE y USUARIO; debe ser null para SUPER_ADMIN
     * @param estado  estado inicial: PENDIENTE para un autorregistro, ACTIVO para un alta ya
     *                autorizada (gerente fundador, invitación, administrador inicial)
     */
    public Usuario(String username, String email, String passwordHash, String nombre, Rol rol, Empresa empresa,
                   EstadoUsuario estado) {
        // Asignación directa: invocar setters sobrescribibles desde el constructor es inseguro.
        this.username = normalizarUsername(Objects.requireNonNull(username, "username"));
        this.email = normalizarEmail(Objects.requireNonNull(email, "email"));
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.nombre = Objects.requireNonNull(nombre, "nombre").strip();
        this.rol = Objects.requireNonNull(rol, "rol");
        this.empresa = validarEmpresa(rol, empresa);
        fijarEstado(Objects.requireNonNull(estado, "estado"));
    }

    private static Empresa validarEmpresa(Rol rol, Empresa empresa) {
        if (rol.perteneceAEmpresa() && empresa == null) {
            throw new IllegalArgumentException("Un " + rol + " debe pertenecer a una empresa.");
        }
        if (!rol.perteneceAEmpresa() && empresa != null) {
            throw new IllegalArgumentException("Un SUPER_ADMIN no pertenece a ninguna empresa.");
        }
        return empresa;
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

    /** Mantiene la regla rol/empresa: pasar a o desde SUPER_ADMIN exige cambiar también la empresa. */
    public void setRol(Rol rol) {
        validarEmpresa(rol, empresa);
        this.rol = Objects.requireNonNull(rol, "rol");
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    /** Sin inicializar el proxy: el id de una asociación LAZY ya está cargado. */
    public Long getEmpresaId() {
        return empresa == null ? null : empresa.getId();
    }

    public EstadoUsuario getEstado() {
        return estado;
    }

    /** Tiene acceso operativo (estado ACTIVO). */
    public boolean isActivo() {
        return estado.permiteAcceso();
    }

    /**
     * Aprueba, rechaza, desactiva o reactiva la cuenta según las transiciones de
     * {@link EstadoUsuario#puedePasarA}.
     *
     * @throws CampoInvalidoException si el destino es PENDIENTE (solo se llega registrándose)
     * @throws ConflictoException     si la cuenta ya está en ese estado o la transición no existe
     */
    public void cambiarEstado(EstadoUsuario destino) {
        Objects.requireNonNull(destino, "destino");
        if (destino == EstadoUsuario.PENDIENTE) {
            throw new CampoInvalidoException("estado", "El nuevo estado debe ser ACTIVO, RECHAZADO o INACTIVO.");
        }
        if (destino == estado) {
            throw new ConflictoException("La cuenta ya está en estado " + estado + ".");
        }
        if (!estado.puedePasarA(destino)) {
            throw new ConflictoException("Una cuenta en estado " + estado + " no puede pasar a " + destino + ".");
        }
        fijarEstado(destino);
    }

    private void fijarEstado(EstadoUsuario nuevo) {
        this.estado = nuevo;
        this.activo = nuevo.permiteAcceso();
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
        return "Usuario{id=" + id + ", username='" + username + "', rol=" + rol + ", estado=" + estado + '}';
    }
}
