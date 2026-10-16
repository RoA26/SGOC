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
import java.math.RoundingMode;
import java.util.Objects;

/** Línea de una orden de compra: producto, cantidad pedida, costo unitario pactado y lo ya recibido. */
@Entity
@Table(name = "detalles_orden_compra")
public class DetalleOrdenCompra extends EntidadDeEmpresa {

    public static final int CANTIDAD_MAXIMA = 999_999;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_id", nullable = false, updatable = false)
    private OrdenCompra orden;

    /** {@link ProductoReferencia}: la línea se sigue leyendo aunque el producto se dé de baja más tarde. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false, updatable = false)
    private ProductoReferencia producto;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "costo_unitario", nullable = false, precision = 14, scale = 2)
    private BigDecimal costoUnitario;

    @Column(name = "cantidad_recibida", nullable = false)
    private int cantidadRecibida;

    /** Requerido por JPA. */
    protected DetalleOrdenCompra() {
    }

    /** Solo la crea {@link OrdenCompra#definirLineas}, que mantiene la relación bidireccional. */
    DetalleOrdenCompra(OrdenCompra orden, ProductoReferencia producto, int cantidad, BigDecimal costoUnitario) {
        this.orden = Objects.requireNonNull(orden, "orden");
        this.producto = Objects.requireNonNull(producto, "producto");
        actualizar(cantidad, costoUnitario);
    }

    void actualizar(int cantidad, BigDecimal costoUnitario) {
        if (cantidad <= 0 || cantidad > CANTIDAD_MAXIMA) {
            throw new IllegalArgumentException("La cantidad debe estar entre 1 y " + CANTIDAD_MAXIMA + ".");
        }
        if (Objects.requireNonNull(costoUnitario, "costoUnitario").signum() <= 0) {
            throw new IllegalArgumentException("El costo unitario debe ser mayor que 0.");
        }
        this.cantidad = cantidad;
        this.costoUnitario = costoUnitario.setScale(2, RoundingMode.HALF_UP);
    }

    /** Unidades que faltan por recibir. */
    public int pendiente() {
        return cantidad - cantidadRecibida;
    }

    public boolean estaCompleta() {
        return cantidadRecibida == cantidad;
    }

    /**
     * Suma unidades recibidas. El servicio ya validó la cantidad contra lo pendiente; esto es
     * la invariante (y ck_detalles_orden_compra_recibida, la de la BD).
     */
    public void recibir(int unidades) {
        if (unidades <= 0 || unidades > pendiente()) {
            throw new IllegalArgumentException("Cantidad recibida fuera de rango: " + unidades);
        }
        this.cantidadRecibida += unidades;
    }

    public BigDecimal subtotal() {
        return costoUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    public Long getId() {
        return id;
    }

    public OrdenCompra getOrden() {
        return orden;
    }

    public ProductoReferencia getProducto() {
        return producto;
    }

    public int getCantidad() {
        return cantidad;
    }

    public BigDecimal getCostoUnitario() {
        return costoUnitario;
    }

    public int getCantidadRecibida() {
        return cantidadRecibida;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DetalleOrdenCompra that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return DetalleOrdenCompra.class.hashCode();
    }

    @Override
    public String toString() {
        return "DetalleOrdenCompra{id=" + id + ", cantidad=" + cantidad + ", recibida=" + cantidadRecibida + '}';
    }
}
