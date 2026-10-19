package com.unisen.sgp.model.entity;

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
import java.util.Objects;
import org.hibernate.annotations.Immutable;

/** Novedad informada al registrar una recepción (faltante, daño, excedente, producto incorrecto…). */
@Entity
@Immutable
@Table(name = "incidencias_recepcion")
public class IncidenciaRecepcion extends EntidadDeEmpresa {

    public static final int DESCRIPCION_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recepcion_id", nullable = false, updatable = false)
    private Recepcion recepcion;

    /** Línea de la orden afectada; null en PRODUCTO_INCORRECTO u OTRO sin línea. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detalle_orden_id", updatable = false)
    private DetalleOrdenCompra detalleOrden;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 25)
    private TipoIncidencia tipo;

    @Column(updatable = false)
    private Integer cantidad;

    @Column(updatable = false, length = DESCRIPCION_MAX_LENGTH)
    private String descripcion;

    /** Requerido por JPA. */
    protected IncidenciaRecepcion() {
    }

    /** Solo la crea {@link Recepcion#reportar}; las reglas de cada tipo, aquí (y en ck_incidencias_recepcion_datos). */
    IncidenciaRecepcion(Recepcion recepcion, TipoIncidencia tipo, DetalleOrdenCompra detalleOrden, Integer cantidad,
                        String descripcion) {
        Objects.requireNonNull(tipo, "tipo");
        if (tipo.porLinea() && (detalleOrden == null || cantidad == null)) {
            throw new IllegalArgumentException(tipo + " necesita la línea de la orden y la cantidad.");
        }
        if (!tipo.porLinea() && descripcion == null) {
            throw new IllegalArgumentException(tipo + " necesita una descripción.");
        }
        if (cantidad != null && cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad de una incidencia debe ser mayor que 0.");
        }
        this.recepcion = Objects.requireNonNull(recepcion, "recepcion");
        this.tipo = tipo;
        this.detalleOrden = detalleOrden;
        this.cantidad = cantidad;
        this.descripcion = descripcion;
    }

    public Long getId() {
        return id;
    }

    public Recepcion getRecepcion() {
        return recepcion;
    }

    public DetalleOrdenCompra getDetalleOrden() {
        return detalleOrden;
    }

    public TipoIncidencia getTipo() {
        return tipo;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public String getDescripcion() {
        return descripcion;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IncidenciaRecepcion that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return IncidenciaRecepcion.class.hashCode();
    }

    @Override
    public String toString() {
        return "IncidenciaRecepcion{id=" + id + ", tipo=" + tipo + ", cantidad=" + cantidad + '}';
    }
}
