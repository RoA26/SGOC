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
import java.util.Objects;

/**
 * Línea de venta con la copia histórica de todo lo aplicado. Solo cambia la cantidad devuelta,
 * siempre a través de {@link #devolver} (con la venta bloqueada).
 */
@Entity
@Table(name = "detalles_venta")
public class DetalleVenta extends EntidadDeEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false, updatable = false)
    private Venta venta;

    @Column(nullable = false, updatable = false)
    private int linea;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false, updatable = false)
    private ProductoReferencia producto;

    @Column(nullable = false, updatable = false, length = 40)
    private String sku;

    @Column(nullable = false, updatable = false, length = 150)
    private String nombre;

    @Column(nullable = false, updatable = false)
    private int cantidad;

    @Column(name = "precio_unitario", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal precioUnitario;

    @Enumerated(EnumType.STRING)
    @Column(name = "descuento_tipo", updatable = false, length = 10)
    private TipoDescuento descuentoTipo;

    @Column(name = "descuento_valor", updatable = false, precision = 14, scale = 2)
    private BigDecimal descuentoValor;

    @Column(nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "descuento_linea", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal descuentoLinea;

    @Column(name = "descuento_general", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal descuentoGeneral;

    @Column(name = "base_gravable", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal baseGravable;

    @Enumerated(EnumType.STRING)
    @Column(name = "tratamiento_iva", nullable = false, updatable = false, length = 12)
    private TratamientoIva tratamientoIva;

    @Column(name = "tarifa_iva", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal tarifaIva;

    @Column(nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal impuesto;

    @Column(nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Column(name = "costo_unitario", updatable = false, precision = 14, scale = 4)
    private BigDecimal costoUnitario;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_costo", nullable = false, updatable = false, length = 25)
    private MetodoCosto metodoCosto;

    @Column(name = "bajo_costo", nullable = false, updatable = false)
    private boolean bajoCosto;

    @Column(name = "cantidad_devuelta", nullable = false)
    private int cantidadDevuelta;

    protected DetalleVenta() {
    }

    public DetalleVenta(int linea, ProductoReferencia producto, int cantidad, BigDecimal precioUnitario,
                        TipoDescuento descuentoTipo, BigDecimal descuentoValor, BigDecimal subtotal,
                        BigDecimal descuentoLinea, BigDecimal descuentoGeneral, BigDecimal baseGravable,
                        TratamientoIva tratamientoIva, BigDecimal tarifaIva, BigDecimal impuesto, BigDecimal total,
                        BigDecimal costoUnitario, boolean bajoCosto) {
        this.linea = linea;
        this.producto = Objects.requireNonNull(producto, "producto");
        this.sku = producto.getSku();
        this.nombre = producto.getNombre();
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.descuentoTipo = descuentoTipo;
        this.descuentoValor = descuentoValor;
        this.subtotal = subtotal;
        this.descuentoLinea = descuentoLinea;
        this.descuentoGeneral = descuentoGeneral;
        this.baseGravable = baseGravable;
        this.tratamientoIva = tratamientoIva;
        this.tarifaIva = tarifaIva;
        this.impuesto = impuesto;
        this.total = total;
        this.costoUnitario = costoUnitario;
        this.metodoCosto = costoUnitario == null ? MetodoCosto.DESCONOCIDO : MetodoCosto.PROMEDIO_PONDERADO;
        this.bajoCosto = bajoCosto;
    }

    void asignarVenta(Venta venta) {
        this.venta = venta;
    }

    public int pendienteDeDevolver() {
        return cantidad - cantidadDevuelta;
    }

    public void devolver(int unidades) {
        if (unidades <= 0 || unidades > pendienteDeDevolver()) {
            throw new IllegalArgumentException("Cantidad a devolver no válida: " + unidades);
        }
        cantidadDevuelta += unidades;
    }

    /** Costo histórico de las unidades de la línea (null si se desconoce). */
    public BigDecimal costoTotal() {
        return costoUnitario == null ? null : costoUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    public Long getId() { return id; }
    public Venta getVenta() { return venta; }
    public int getLinea() { return linea; }
    public ProductoReferencia getProducto() { return producto; }
    public String getSku() { return sku; }
    public String getNombre() { return nombre; }
    public int getCantidad() { return cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public TipoDescuento getDescuentoTipo() { return descuentoTipo; }
    public BigDecimal getDescuentoValor() { return descuentoValor; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDescuentoLinea() { return descuentoLinea; }
    public BigDecimal getDescuentoGeneral() { return descuentoGeneral; }
    public BigDecimal getBaseGravable() { return baseGravable; }
    public TratamientoIva getTratamientoIva() { return tratamientoIva; }
    public BigDecimal getTarifaIva() { return tarifaIva; }
    public BigDecimal getImpuesto() { return impuesto; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getCostoUnitario() { return costoUnitario; }
    public MetodoCosto getMetodoCosto() { return metodoCosto; }
    public boolean isBajoCosto() { return bajoCosto; }
    public int getCantidadDevuelta() { return cantidadDevuelta; }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof DetalleVenta that && id != null && id.equals(that.getId()));
    }

    @Override
    public int hashCode() {
        return DetalleVenta.class.hashCode();
    }
}
