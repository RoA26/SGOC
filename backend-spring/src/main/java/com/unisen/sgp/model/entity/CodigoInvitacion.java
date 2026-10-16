package com.unisen.sgp.model.entity;

import com.unisen.sgp.exception.ConflictoException;
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
 * Código de invitación de un solo uso y con caducidad: un administrador lo genera para su empresa
 * y la persona invitada lo canjea en {@code POST /api/auth/registro}, quedando como TRABAJADOR
 * ACTIVO de esa empresa. Su validez (sin usar, sin revocar y sin caducar) se comprueba al
 * canjearlo, con la fila bloqueada; no depende de ningún proceso programado.
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

    /** Revocación (baja lógica): quién y cuándo. Ambas nulas mientras no se revoque. */
    @Column(name = "fecha_revocacion")
    private Instant fechaRevocacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revocado_por_id")
    private Usuario revocadoPor;

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
        switch (estado(ahora)) {
            case USADA -> throw new InvitacionInvalidaException(Motivo.USADO);
            case REVOCADA -> throw new InvitacionInvalidaException(Motivo.REVOCADO);
            case CADUCADA -> throw new InvitacionInvalidaException(Motivo.CADUCADO);
            case VIGENTE -> {
                // Se puede canjear.
            }
        }
    }

    /** Situación en el instante indicado. Usada y revocada son definitivas; la caducidad depende de la hora. */
    public EstadoInvitacion estado(Instant ahora) {
        if (usado) {
            return EstadoInvitacion.USADA;
        }
        if (fechaRevocacion != null) {
            return EstadoInvitacion.REVOCADA;
        }
        return fechaExpiracion.isAfter(ahora) ? EstadoInvitacion.VIGENTE : EstadoInvitacion.CADUCADA;
    }

    /**
     * Anula una invitación vigente: deja de aceptarse y se conserva para la auditoría. Debe
     * llamarse con la fila bloqueada, para no competir con un canje simultáneo.
     *
     * @throws ConflictoException si ya se usó, ya estaba revocada o ya caducó (→ 409)
     */
    public void revocar(Usuario revocador, Instant ahora) {
        switch (estado(ahora)) {
            case USADA -> throw new ConflictoException("La invitación ya fue utilizada: no se puede revocar.");
            case REVOCADA -> throw new ConflictoException("La invitación ya estaba revocada.");
            case CADUCADA -> throw new ConflictoException("La invitación ya caducó: no hace falta revocarla.");
            case VIGENTE -> {
                this.fechaRevocacion = Objects.requireNonNull(ahora, "ahora");
                this.revocadoPor = Objects.requireNonNull(revocador, "revocador");
            }
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

    public Instant getFechaRevocacion() {
        return fechaRevocacion;
    }

    public Usuario getRevocadoPor() {
        return revocadoPor;
    }

    /** Solo los 4 últimos caracteres: el código completo es una credencial y solo se muestra al generarlo. */
    public String getCodigoParcial() {
        return "••••-••••-••••-" + codigo.substring(Math.max(0, codigo.length() - 4));
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
        return "CodigoInvitacion{id=" + id + ", usado=" + usado + ", revocada=" + (fechaRevocacion != null)
                + ", fechaExpiracion=" + fechaExpiracion + '}';
    }
}
