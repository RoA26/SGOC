package com.unisen.sgp.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import org.hibernate.annotations.Immutable;

/** Unidades recibidas de una línea de la orden en una recepción concreta. */
@Entity
@Immutable
@Table(name = "detalles_recepcion")
public class DetalleRecepcion extends EntidadDeEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recepcion_id", nullable = false, updatable = false)
    private Recepcion recepcion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "detalle_orden_id", nullable = false, updatable = false)
    private DetalleOrdenCompra detalleOrden;

    @Column(nullable = false, updatable = false)
    private int cantidad;

    /** Requerido por JPA. */
    protected DetalleRecepcion() {
    }

    /** Solo la crea {@link Recepcion#registrar}. */
    DetalleRecepcion(Recepcion recepcion, DetalleOrdenCompra detalleOrden, int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad recibida debe ser mayor que 0.");
        }
        this.recepcion = Objects.requireNonNull(recepcion, "recepcion");
        this.detalleOrden = Objects.requireNonNull(detalleOrden, "detalleOrden");
        this.cantidad = cantidad;
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

    public int getCantidad() {
        return cantidad;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DetalleRecepcion that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return DetalleRecepcion.class.hashCode();
    }

    @Override
    public String toString() {
        return "DetalleRecepcion{id=" + id + ", cantidad=" + cantidad + '}';
    }
}
