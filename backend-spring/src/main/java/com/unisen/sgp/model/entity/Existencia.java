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
import java.time.Instant;
import java.util.Objects;

/**
 * Saldo actual de un producto (una fila por producto, creada con él). Solo cambia a través de
 * {@link #aplicar}, siempre con la fila bloqueada y dejando un {@link MovimientoInventario}: el
 * saldo es la suma de los movimientos.
 */
@Entity
@Table(name = "existencias")
public class Existencia extends EntidadDeEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "producto_id", nullable = false, updatable = false)
    private Long productoId;

    /** Asociación de solo lectura sobre la misma columna, para consultar el producto. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", insertable = false, updatable = false)
    private ProductoReferencia producto;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Requerido por JPA. */
    protected Existencia() {
    }

    /** Un producto recién creado empieza sin unidades. */
    public Existencia(Long productoId, Instant ahora) {
        this.productoId = Objects.requireNonNull(productoId, "productoId");
        this.actualizadoEn = Objects.requireNonNull(ahora, "ahora");
    }

    /**
     * Suma (o resta, con signo negativo) unidades y devuelve el saldo resultante. El servicio
     * ya comprobó que no queda negativo; esto es la invariante (y ck_existencias_cantidad, la de la BD).
     */
    public int aplicar(int unidades, Instant ahora) {
        long saldo = (long) cantidad + unidades;
        if (unidades == 0 || saldo < 0 || saldo > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Movimiento de inventario no válido: " + unidades);
        }
        this.cantidad = (int) saldo;
        this.actualizadoEn = Objects.requireNonNull(ahora, "ahora");
        return cantidad;
    }

    public Long getId() {
        return id;
    }

    public Long getProductoId() {
        return productoId;
    }

    public ProductoReferencia getProducto() {
        return producto;
    }

    public int getCantidad() {
        return cantidad;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Existencia that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Existencia.class.hashCode();
    }

    @Override
    public String toString() {
        return "Existencia{productoId=" + productoId + ", cantidad=" + cantidad + '}';
    }
}
