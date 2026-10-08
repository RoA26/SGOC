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
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Producto del catálogo, suministrado por un único proveedor.
 *
 * <p>También usa borrado lógico: las futuras órdenes de compra lo referenciarán y un borrado
 * físico rompería ese histórico.
 */
@Entity
@Table(name = "productos")
@SQLDelete(sql = "UPDATE productos SET activo = false, actualizado_en = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("activo = true")
public class Producto extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Único por empresa (uq_productos_sku = empresa_id + sku). */
    @Column(nullable = false, length = 40)
    private String sku;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 1000)
    private String descripcion;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal precio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @Column(nullable = false)
    private boolean activo = true;

    /** Requerido por JPA. */
    protected Producto() {
    }

    public Producto(String sku, String nombre, String descripcion, BigDecimal precio, Proveedor proveedor) {
        asignarDatos(sku, nombre, descripcion, precio, proveedor);
    }

    public void actualizar(String sku, String nombre, String descripcion, BigDecimal precio, Proveedor proveedor) {
        asignarDatos(sku, nombre, descripcion, precio, proveedor);
    }

    private void asignarDatos(String sku, String nombre, String descripcion, BigDecimal precio, Proveedor proveedor) {
        this.sku = Objects.requireNonNull(sku, "sku");
        this.nombre = Objects.requireNonNull(nombre, "nombre");
        this.descripcion = descripcion;
        // Escala fija de 2 decimales (la validación ya garantiza que no hay más).
        this.precio = Objects.requireNonNull(precio, "precio").setScale(2, RoundingMode.HALF_UP);
        this.proveedor = Objects.requireNonNull(proveedor, "proveedor");
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public Proveedor getProveedor() {
        return proveedor;
    }

    public boolean isActivo() {
        return activo;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Producto that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Producto.class.hashCode();
    }

    @Override
    public String toString() {
        return "Producto{id=" + id + ", sku='" + sku + "', activo=" + activo + '}';
    }
}
