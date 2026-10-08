package com.unisen.sgp.model.entity;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.exception.ConflictoException;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Solicitud interna de compra: lo que un trabajador necesita y por qué. Es la raíz del
 * agregado maestro-detalle: las líneas ({@link DetalleSolicitud}) se guardan y se borran con
 * ella (cascade + orphanRemoval), siempre en la misma transacción.
 */
@Entity
@Table(name = "solicitudes")
public class Solicitud extends EntidadDeEmpresa {

    public static final int JUSTIFICACION_MAX_LENGTH = 1000;
    public static final int COMENTARIO_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario solicitante;

    @Column(nullable = false, updatable = false)
    private Instant fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSolicitud estado = EstadoSolicitud.PENDIENTE;

    @Column(nullable = false, length = JUSTIFICACION_MAX_LENGTH)
    private String justificacion;

    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<DetalleSolicitud> detalles = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revisado_por_id")
    private Usuario revisadoPor;

    @Column(name = "fecha_revision")
    private Instant fechaRevision;

    @Column(name = "comentario_revision", length = COMENTARIO_MAX_LENGTH)
    private String comentarioRevision;

    /** Requerido por JPA. */
    protected Solicitud() {
    }

    /** Toda solicitud nace PENDIENTE. */
    public Solicitud(Usuario solicitante, String justificacion, Instant fecha) {
        this.solicitante = Objects.requireNonNull(solicitante, "solicitante");
        this.justificacion = Objects.requireNonNull(justificacion, "justificacion");
        this.fecha = Objects.requireNonNull(fecha, "fecha");
    }

    /** Añade una línea manteniendo ambos lados de la relación sincronizados. */
    public void agregarDetalle(ProductoReferencia producto, int cantidad) {
        detalles.add(new DetalleSolicitud(this, producto, cantidad));
    }

    /**
     * Aprueba o rechaza la solicitud.
     *
     * @throws ConflictoException     si ya fue revisada (los estados finales no cambian)
     * @throws CampoInvalidoException si el nuevo estado no es una revisión o falta el motivo del rechazo
     */
    public void revisar(EstadoSolicitud nuevoEstado, Usuario revisor, String comentario, Instant ahora) {
        if (estado != EstadoSolicitud.PENDIENTE) {
            throw new ConflictoException("La solicitud ya fue " + estado.descripcion() + "; su estado no puede cambiar.");
        }
        if (nuevoEstado == EstadoSolicitud.PENDIENTE) {
            throw new CampoInvalidoException("estado", "El nuevo estado debe ser APROBADA o RECHAZADA.");
        }
        if (nuevoEstado == EstadoSolicitud.RECHAZADA && comentario == null) {
            throw new CampoInvalidoException("comentario", "Indica el motivo del rechazo.");
        }
        this.estado = Objects.requireNonNull(nuevoEstado, "nuevoEstado");
        this.revisadoPor = Objects.requireNonNull(revisor, "revisor");
        this.fechaRevision = Objects.requireNonNull(ahora, "ahora");
        this.comentarioRevision = comentario;
    }

    public boolean perteneceA(Long usuarioId) {
        return solicitante.getId().equals(usuarioId);
    }

    public Long getId() {
        return id;
    }

    public Usuario getSolicitante() {
        return solicitante;
    }

    public Instant getFecha() {
        return fecha;
    }

    public EstadoSolicitud getEstado() {
        return estado;
    }

    public String getJustificacion() {
        return justificacion;
    }

    /** Solo lectura: las líneas se añaden con {@link #agregarDetalle}. */
    public List<DetalleSolicitud> getDetalles() {
        return Collections.unmodifiableList(detalles);
    }

    public Usuario getRevisadoPor() {
        return revisadoPor;
    }

    public Instant getFechaRevision() {
        return fechaRevision;
    }

    public String getComentarioRevision() {
        return comentarioRevision;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Solicitud that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Solicitud.class.hashCode();
    }

    @Override
    public String toString() {
        return "Solicitud{id=" + id + ", estado=" + estado + ", lineas=" + detalles.size() + '}';
    }
}
