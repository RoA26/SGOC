package com.unisen.sgp.model.entity;

import com.unisen.sgp.exception.InvitacionInvalidaException;
import com.unisen.sgp.exception.InvitacionInvalidaException.Motivo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * Código de invitación de un solo uso y con caducidad. Es la única vía de alta de usuarios:
 * un gestor lo genera para su empresa y la persona invitada lo canjea en
 * {@code POST /api/auth/registro}, quedando como USUARIO de esa empresa.
 */
@Entity
@Table(name = "codigos_invitacion")
public class CodigoInvitacion extends EntidadDeEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String codigo;

    @Column(name = "fecha_expiracion", nullable = false)
    private Instant fechaExpiracion;

    @Column(nullable = false)
    private boolean usado = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_creador_id", nullable = false)
    private Usuario creador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usado_por_id")
    private Usuario usadoPor;

    @Column(name = "fecha_uso")
    private Instant fechaUso;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    /** Requerido por JPA. */
    protected CodigoInvitacion() {
    }

    public CodigoInvitacion(String codigo, Instant fechaExpiracion, Usuario creador, Instant creadoEn) {
        this.codigo = Objects.requireNonNull(codigo, "codigo");
        this.fechaExpiracion = Objects.requireNonNull(fechaExpiracion, "fechaExpiracion");
        this.creador = Objects.requireNonNull(creador, "creador");
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn");
    }

    /**
     * Comprueba que el código se puede canjear en el instante indicado.
     *
     * @throws InvitacionInvalidaException si ya se usó o ha caducado
     */
    public void validarCanjeable(Instant ahora) {
        if (usado) {
            throw new InvitacionInvalidaException(Motivo.USADO);
        }
        if (!fechaExpiracion.isAfter(ahora)) {
            throw new InvitacionInvalidaException(Motivo.CADUCADO);
        }
    }

    /** Marca el código como consumido por {@code usuario}. Debe ocurrir en la misma transacción que el alta. */
    public void marcarUsado(Usuario usuario, Instant ahora) {
        validarCanjeable(ahora);
        this.usado = true;
        this.usadoPor = Objects.requireNonNull(usuario, "usuario");
        this.fechaUso = Objects.requireNonNull(ahora, "ahora");
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public Instant getFechaExpiracion() {
        return fechaExpiracion;
    }

    public boolean isUsado() {
        return usado;
    }

    public Usuario getCreador() {
        return creador;
    }

    public Usuario getUsadoPor() {
        return usadoPor;
    }

    public Instant getFechaUso() {
        return fechaUso;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CodigoInvitacion that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return CodigoInvitacion.class.hashCode();
    }

    /** Nunca incluye el código: es una credencial. */
    @Override
    public String toString() {
        return "CodigoInvitacion{id=" + id + ", usado=" + usado + ", fechaExpiracion=" + fechaExpiracion + '}';
    }
}
