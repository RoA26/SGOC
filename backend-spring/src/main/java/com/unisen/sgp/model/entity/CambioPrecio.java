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
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.Immutable;

/**
 * Un cambio de las condiciones de venta de un producto (precio sin IVA, tratamiento y tarifa del
 * IVA) con los valores anteriores y nuevos. Solo inserción: el historial no se reescribe.
 */
@Entity
@Immutable
@Table(name = "cambios_precio")
public class CambioPrecio extends EntidadDeEmpresa {

    public static final int MOTIVO_MAX_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false, updatable = false)
    private ProductoReferencia producto;

    @Column(name = "precio_anterior", updatable = false, precision = 14, scale = 2)
    private BigDecimal precioAnterior;

    @Column(name = "precio_nuevo", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal precioNuevo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tratamiento_anterior", updatable = false, length = 12)
    private TratamientoIva tratamientoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "tratamiento_nuevo", nullable = false, updatable = false, length = 12)
    private TratamientoIva tratamientoNuevo;

    @Column(name = "tarifa_anterior", updatable = false, precision = 5, scale = 2)
    private BigDecimal tarifaAnterior;

    @Column(name = "tarifa_nueva", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal tarifaNueva;

    @Column(name = "costo_referencia", updatable = false, precision = 14, scale = 4)
    private BigDecimal costoReferencia;

    @Column(updatable = false, length = MOTIVO_MAX_LENGTH)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(nullable = false, updatable = false)
    private Instant fecha;

    protected CambioPrecio() {
    }

    public CambioPrecio(ProductoReferencia producto, BigDecimal precioAnterior, TratamientoIva tratamientoAnterior,
                        BigDecimal tarifaAnterior, Producto actualizado, BigDecimal costoReferencia, String motivo,
                        Usuario usuario, Instant fecha) {
        this.producto = Objects.requireNonNull(producto, "producto");
        this.precioAnterior = precioAnterior;
        this.tratamientoAnterior = tratamientoAnterior;
        this.tarifaAnterior = tarifaAnterior;
        this.precioNuevo = Objects.requireNonNull(actualizado.getPrecioVenta(), "precioNuevo");
        this.tratamientoNuevo = Objects.requireNonNull(actualizado.getTratamientoIva(), "tratamientoNuevo");
        this.tarifaNueva = Objects.requireNonNull(actualizado.getTarifaIva(), "tarifaNueva");
        this.costoReferencia = costoReferencia;
        this.motivo = motivo;
        this.usuario = Objects.requireNonNull(usuario, "usuario");
        this.fecha = Objects.requireNonNull(fecha, "fecha");
    }

    public Long getId() { return id; }
    public ProductoReferencia getProducto() { return producto; }
    public BigDecimal getPrecioAnterior() { return precioAnterior; }
    public BigDecimal getPrecioNuevo() { return precioNuevo; }
    public TratamientoIva getTratamientoAnterior() { return tratamientoAnterior; }
    public TratamientoIva getTratamientoNuevo() { return tratamientoNuevo; }
    public BigDecimal getTarifaAnterior() { return tarifaAnterior; }
    public BigDecimal getTarifaNueva() { return tarifaNueva; }
    public BigDecimal getCostoReferencia() { return costoReferencia; }
    public String getMotivo() { return motivo; }
    public Usuario getUsuario() { return usuario; }
    public Instant getFecha() { return fecha; }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof CambioPrecio that && id != null && id.equals(that.getId()));
    }

    @Override
    public int hashCode() {
        return CambioPrecio.class.hashCode();
    }
}
