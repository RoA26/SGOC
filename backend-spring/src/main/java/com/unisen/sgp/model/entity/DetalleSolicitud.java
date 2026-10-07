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
import java.math.BigDecimal;
import java.util.Objects;

/** Línea de una solicitud: un producto del catálogo y la cantidad pedida. */
@Entity
@Table(name = "detalles_solicitud")
public class DetalleSolicitud {

    public static final int CANTIDAD_MAXIMA = 999_999;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_id", nullable = false, updatable = false)
    private Solicitud solicitud;

    /** {@link ProductoReferencia} y no {@link Producto}: la línea debe poder leerse aunque el producto se dé de baja. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false, updatable = false)
    private ProductoReferencia producto;

    @Column(nullable = false)
    private int cantidad;

    /** Requerido por JPA. */
    protected DetalleSolicitud() {
    }

    /** Solo la crea {@link Solicitud#agregarDetalle}, que mantiene la relación bidireccional. */
    DetalleSolicitud(Solicitud solicitud, ProductoReferencia producto, int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que 0.");
        }
        this.solicitud = Objects.requireNonNull(solicitud, "solicitud");
        this.producto = Objects.requireNonNull(producto, "producto");
        this.cantidad = cantidad;
    }

    /** Importe orientativo con el precio actual del catálogo (la solicitud no fija precios). */
    public BigDecimal subtotalEstimado() {
        return producto.getPrecio().multiply(BigDecimal.valueOf(cantidad));
    }

    public Long getId() {
        return id;
    }

    public Solicitud getSolicitud() {
        return solicitud;
    }

    public ProductoReferencia getProducto() {
        return producto;
    }

    public int getCantidad() {
        return cantidad;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DetalleSolicitud that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return DetalleSolicitud.class.hashCode();
    }

    @Override
    public String toString() {
        return "DetalleSolicitud{id=" + id + ", cantidad=" + cantidad + '}';
    }
}
