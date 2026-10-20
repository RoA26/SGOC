package com.unisen.sgp.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;

/**
 * Vista de solo lectura de la tabla {@code productos} para los documentos que la referencian
 * (líneas de solicitud y de órdenes, existencias, movimientos). Como toda entidad de empresa, solo ve los productos del tenant
 * actual; a diferencia de {@link Producto}, no lleva {@code @SQLRestriction}:
 * un producto dado de baja sigue apareciendo en las solicitudes históricas que lo incluyen.
 * Las altas, cambios y bajas se hacen siempre a través de {@link Producto}.
 */
@Entity
@Immutable
@Table(name = "productos")
public class ProductoReferencia extends EntidadDeEmpresa {

    @Id
    private Long id;

    @Column(nullable = false, insertable = false, updatable = false)
    private String sku;

    @Column(nullable = false, insertable = false, updatable = false)
    private String nombre;

    @Column(nullable = false, insertable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal precio;

    @Column(nullable = false, insertable = false, updatable = false)
    private boolean activo;

    @Column(name = "proveedor_id", nullable = false, insertable = false, updatable = false)
    private Long proveedorId;

    @Column(name = "codigo_barras", insertable = false, updatable = false)
    private String codigoBarras;

    @Column(name = "stock_minimo", nullable = false, insertable = false, updatable = false)
    private int stockMinimo;

    @Column(name = "precio_venta", insertable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal precioVenta;

    @Enumerated(EnumType.STRING)
    @Column(name = "tratamiento_iva", insertable = false, updatable = false, length = 12)
    private TratamientoIva tratamientoIva;

    @Column(name = "tarifa_iva", insertable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal tarifaIva;

    /** Requerido por JPA. */
    protected ProductoReferencia() {
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

    public BigDecimal getPrecio() {
        return precio;
    }

    public boolean isActivo() {
        return activo;
    }

    /** Proveedor actual del producto (las órdenes solo admiten productos de su proveedor). */
    public Long getProveedorId() {
        return proveedorId;
    }

    public String getCodigoBarras() {
        return codigoBarras;
    }

    public int getStockMinimo() {
        return stockMinimo;
    }

    /** Precio de venta sin IVA; null si el producto no está a la venta. */
    public BigDecimal getPrecioVenta() {
        return precioVenta;
    }

    public TratamientoIva getTratamientoIva() {
        return tratamientoIva;
    }

    public BigDecimal getTarifaIva() {
        return tarifaIva;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProductoReferencia that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return ProductoReferencia.class.hashCode();
    }

    @Override
    public String toString() {
        return "ProductoReferencia{id=" + id + ", sku='" + sku + "', activo=" + activo + '}';
    }
}
