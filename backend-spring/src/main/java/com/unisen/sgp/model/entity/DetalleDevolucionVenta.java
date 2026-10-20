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
import org.hibernate.annotations.Immutable;

/**
 * Unidades devueltas de una línea de venta, con su parte de la base, del IVA y del total, y el
 * costo histórico de la línea. {@link #reingresaInventario} indica si vuelven a estar disponibles
 * para la venta (true) o no (p. ej. dañadas): no se presume que todo lo devuelto se pueda revender.
 */
@Entity
@Immutable
@Table(name = "detalles_devolucion_venta")
public class DetalleDevolucionVenta extends EntidadDeEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "devolucion_id", nullable = false, updatable = false)
    private DevolucionVenta devolucion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "detalle_venta_id", nullable = false, updatable = false)
    private DetalleVenta detalleVenta;

    @Column(nullable = false, updatable = false)
    private int cantidad;

    @Column(name = "reingresa_inventario", nullable = false, updatable = false)
    private boolean reingresaInventario;

    @Column(name = "base_gravable", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal baseGravable;

    @Column(nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal impuesto;

    @Column(nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Column(name = "costo_unitario", updatable = false, precision = 14, scale = 4)
    private BigDecimal costoUnitario;

    protected DetalleDevolucionVenta() {
    }

    public DetalleDevolucionVenta(DetalleVenta detalleVenta, int cantidad, boolean reingresaInventario,
                                  BigDecimal baseGravable, BigDecimal impuesto) {
        this.detalleVenta = Objects.requireNonNull(detalleVenta, "detalleVenta");
        this.cantidad = cantidad;
        this.reingresaInventario = reingresaInventario;
        this.baseGravable = baseGravable;
        this.impuesto = impuesto;
        this.total = baseGravable.add(impuesto);
        this.costoUnitario = detalleVenta.getCostoUnitario();
    }

    void asignarDevolucion(DevolucionVenta devolucion) {
        this.devolucion = devolucion;
    }

    public BigDecimal costoTotal() {
        return costoUnitario == null ? null : costoUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    public Long getId() { return id; }
    public DevolucionVenta getDevolucion() { return devolucion; }
    public DetalleVenta getDetalleVenta() { return detalleVenta; }
    public int getCantidad() { return cantidad; }
    public boolean isReingresaInventario() { return reingresaInventario; }
    public BigDecimal getBaseGravable() { return baseGravable; }
    public BigDecimal getImpuesto() { return impuesto; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getCostoUnitario() { return costoUnitario; }
}
