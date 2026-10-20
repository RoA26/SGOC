package com.unisen.sgp.model.entity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Devolución (parcial o total) o anulación de una venta: quién, cuándo, por qué, qué unidades y
 * qué importes se revierten. Inmutable; las unidades que reingresan dejan su movimiento en el kárdex.
 */
@Entity
@Table(name = "devoluciones_venta")
public class DevolucionVenta extends EntidadDeEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false, updatable = false)
    private Venta venta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 12)
    private TipoDevolucion tipo;

    @Column(name = "clave_idempotencia", nullable = false, updatable = false, length = 64)
    private String claveIdempotencia;

    @Column(nullable = false, updatable = false, length = 300)
    private String motivo;

    @Column(name = "base_gravable", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal baseGravable = BigDecimal.ZERO;

    @Column(nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal impuestos = BigDecimal.ZERO;

    @Column(nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "costo_total", updatable = false, precision = 14, scale = 2)
    private BigDecimal costoTotal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registrada_por_id", nullable = false, updatable = false)
    private Usuario registradaPor;

    @Column(name = "registrada_en", nullable = false, updatable = false)
    private Instant registradaEn;

    @OneToMany(mappedBy = "devolucion", cascade = CascadeType.PERSIST)
    @OrderBy("id ASC")
    private List<DetalleDevolucionVenta> detalles = new ArrayList<>();

    protected DevolucionVenta() {
    }

    public DevolucionVenta(Venta venta, TipoDevolucion tipo, String claveIdempotencia, String motivo,
                           Usuario registradaPor, Instant registradaEn) {
        this.venta = Objects.requireNonNull(venta, "venta");
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.claveIdempotencia = Objects.requireNonNull(claveIdempotencia, "claveIdempotencia");
        this.motivo = Objects.requireNonNull(motivo, "motivo");
        this.registradaPor = Objects.requireNonNull(registradaPor, "registradaPor");
        this.registradaEn = Objects.requireNonNull(registradaEn, "registradaEn");
    }

    /** Añade una línea y acumula sus importes; el costo total es desconocido si alguna línea lo es. */
    public void agregar(DetalleDevolucionVenta linea) {
        linea.asignarDevolucion(this);
        detalles.add(linea);
        baseGravable = baseGravable.add(linea.getBaseGravable());
        impuestos = impuestos.add(linea.getImpuesto());
        total = total.add(linea.getTotal());
        boolean costoConocido = detalles.stream().allMatch(d -> d.getCostoUnitario() != null);
        costoTotal = costoConocido
                ? detalles.stream().map(DetalleDevolucionVenta::costoTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, java.math.RoundingMode.HALF_UP)
                : null;
    }

    public Long getId() { return id; }
    public Venta getVenta() { return venta; }
    public TipoDevolucion getTipo() { return tipo; }
    public String getClaveIdempotencia() { return claveIdempotencia; }
    public String getMotivo() { return motivo; }
    public BigDecimal getBaseGravable() { return baseGravable; }
    public BigDecimal getImpuestos() { return impuestos; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getCostoTotal() { return costoTotal; }
    public Usuario getRegistradaPor() { return registradaPor; }
    public Instant getRegistradaEn() { return registradaEn; }
    public List<DetalleDevolucionVenta> getDetalles() { return detalles; }
}
