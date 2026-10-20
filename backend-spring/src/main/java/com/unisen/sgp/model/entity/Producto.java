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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Producto del catálogo, suministrado por un único proveedor.
 *
 * <p>También usa borrado lógico: órdenes de compra, recepciones y movimientos de inventario lo
 * referencian y un borrado físico rompería ese histórico.
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

    /**
     * Opcional y único por empresa (uq_productos_codigo_barras). Texto: conserva los ceros a la
     * izquierda de EAN/UPC.
     */
    @Column(name = "codigo_barras", length = 64)
    private String codigoBarras;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 1000)
    private String descripcion;

    /** Precio de referencia de COMPRA (estimado de solicitudes y costo propuesto en las órdenes). */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal precio;

    /**
     * Precio de venta unitario SIN IVA; null = el producto aún no está a la venta. Solo lo cambia
     * el administrador comercial ({@link #definirCondicionesVenta}), con historial.
     */
    @Column(name = "precio_venta", precision = 14, scale = 2)
    private BigDecimal precioVenta;

    @Enumerated(EnumType.STRING)
    @Column(name = "tratamiento_iva", length = 12)
    private TratamientoIva tratamientoIva;

    /** Tarifa del IVA en %; 0 para exentos y excluidos. */
    @Column(name = "tarifa_iva", precision = 5, scale = 2)
    private BigDecimal tarifaIva;

    /** Con existencias iguales o inferiores, el producto está en stock crítico. 0 = sin mínimo. */
    @Column(name = "stock_minimo", nullable = false)
    private int stockMinimo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @Column(nullable = false)
    private boolean activo = true;

    /** Requerido por JPA. */
    protected Producto() {
    }

    public Producto(Datos datos, Proveedor proveedor) {
        asignarDatos(datos, proveedor);
    }

    public void actualizar(Datos datos, Proveedor proveedor) {
        asignarDatos(datos, proveedor);
    }

    private void asignarDatos(Datos datos, Proveedor proveedor) {
        this.sku = Objects.requireNonNull(datos.sku(), "sku");
        this.codigoBarras = datos.codigoBarras();
        this.nombre = Objects.requireNonNull(datos.nombre(), "nombre");
        this.descripcion = datos.descripcion();
        // Escala fija de 2 decimales (la validación ya garantiza que no hay más).
        this.precio = Objects.requireNonNull(datos.precio(), "precio").setScale(2, RoundingMode.HALF_UP);
        if (datos.stockMinimo() < 0) {
            throw new IllegalArgumentException("El stock mínimo no puede ser negativo.");
        }
        this.stockMinimo = datos.stockMinimo();
        this.proveedor = Objects.requireNonNull(proveedor, "proveedor");
    }

    /**
     * Fija el precio de venta (sin IVA) y el tratamiento del IVA. La coherencia la garantizan
     * también ck_productos_iva y ck_productos_venta_clasificada.
     */
    public void definirCondicionesVenta(BigDecimal precioVenta, TratamientoIva tratamiento, BigDecimal tarifa) {
        Objects.requireNonNull(precioVenta, "precioVenta");
        Objects.requireNonNull(tratamiento, "tratamiento");
        Objects.requireNonNull(tarifa, "tarifa");
        if (precioVenta.signum() <= 0) {
            throw new IllegalArgumentException("El precio de venta debe ser mayor que 0.");
        }
        boolean tarifaValida = tratamiento.cobraImpuesto()
                ? tarifa.signum() > 0 && tarifa.compareTo(BigDecimal.valueOf(100)) < 0
                : tarifa.signum() == 0;
        if (!tarifaValida) {
            throw new IllegalArgumentException("Tarifa " + tarifa + " incompatible con " + tratamiento);
        }
        this.precioVenta = precioVenta.setScale(2, RoundingMode.HALF_UP);
        this.tratamientoIva = tratamiento;
        this.tarifaIva = tarifa.setScale(2, RoundingMode.HALF_UP);
    }

    /** Datos editables del producto (ya validados y normalizados por el DTO). */
    public record Datos(String sku, String codigoBarras, String nombre, String descripcion, BigDecimal precio,
                        int stockMinimo) {
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getCodigoBarras() {
        return codigoBarras;
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

    public BigDecimal getPrecioVenta() {
        return precioVenta;
    }

    public TratamientoIva getTratamientoIva() {
        return tratamientoIva;
    }

    public BigDecimal getTarifaIva() {
        return tarifaIva;
    }

    public int getStockMinimo() {
        return stockMinimo;
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
