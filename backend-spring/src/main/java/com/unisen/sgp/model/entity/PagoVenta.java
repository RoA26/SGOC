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
import org.hibernate.annotations.Immutable;

/** Pago registrado de una venta; en efectivo, con lo recibido y el cambio. */
@Entity
@Immutable
@Table(name = "pagos_venta")
public class PagoVenta extends EntidadDeEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false, updatable = false)
    private Venta venta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private MedioPago medio;

    @Column(nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal valor;

    @Column(updatable = false, precision = 14, scale = 2)
    private BigDecimal recibido;

    @Column(updatable = false, precision = 14, scale = 2)
    private BigDecimal cambio;

    @Column(updatable = false, length = 60)
    private String referencia;

    protected PagoVenta() {
    }

    public PagoVenta(MedioPago medio, BigDecimal valor, BigDecimal recibido, String referencia) {
        this.medio = Objects.requireNonNull(medio, "medio");
        this.valor = Objects.requireNonNull(valor, "valor");
        if (medio == MedioPago.EFECTIVO) {
            this.recibido = recibido == null ? valor : recibido;
            this.cambio = this.recibido.subtract(valor);
        }
        this.referencia = referencia;
    }

    void asignarVenta(Venta venta) {
        this.venta = venta;
    }

    public Long getId() { return id; }
    public MedioPago getMedio() { return medio; }
    public BigDecimal getValor() { return valor; }
    public BigDecimal getRecibido() { return recibido; }
    public BigDecimal getCambio() { return cambio; }
    public String getReferencia() { return referencia; }
}
