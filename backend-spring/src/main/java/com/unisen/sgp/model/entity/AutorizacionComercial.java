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
import java.time.Instant;
import java.util.Objects;

/**
 * Autorización de una excepción comercial: un código de un solo uso, con vencimiento, que el
 * administrador comercial entrega a quien vende para una venta concreta. Cubre un descuento
 * efectivo por línea de hasta {@link #descuentoMaximo} y, si se indica, vender bajo el costo.
 * Al usarse queda enlazada a la venta (quién, cuándo): es la traza de quién autorizó.
 */
@Entity
@Table(name = "autorizaciones_comerciales")
public class AutorizacionComercial extends EntidadDeEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 32)
    private String codigo;

    @Column(name = "descuento_maximo", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal descuentoMaximo;

    @Column(name = "permite_bajo_costo", nullable = false, updatable = false)
    private boolean permiteBajoCosto;

    @Column(nullable = false, updatable = false, length = 300)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creada_por_id", nullable = false, updatable = false)
    private Usuario creadaPor;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    @Column(name = "expira_en", nullable = false, updatable = false)
    private Instant expiraEn;

    @Column(name = "venta_id")
    private Long ventaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usada_por_id")
    private Usuario usadaPor;

    @Column(name = "usada_en")
    private Instant usadaEn;

    protected AutorizacionComercial() {
    }

    public AutorizacionComercial(String codigo, BigDecimal descuentoMaximo, boolean permiteBajoCosto, String motivo,
                                 Usuario creadaPor, Instant creadaEn, Instant expiraEn) {
        this.codigo = Objects.requireNonNull(codigo, "codigo");
        this.descuentoMaximo = Objects.requireNonNull(descuentoMaximo, "descuentoMaximo");
        this.permiteBajoCosto = permiteBajoCosto;
        this.motivo = Objects.requireNonNull(motivo, "motivo");
        this.creadaPor = Objects.requireNonNull(creadaPor, "creadaPor");
        this.creadaEn = Objects.requireNonNull(creadaEn, "creadaEn");
        this.expiraEn = Objects.requireNonNull(expiraEn, "expiraEn");
    }

    public boolean usada() {
        return ventaId != null;
    }

    public boolean vencida(Instant ahora) {
        return !ahora.isBefore(expiraEn);
    }

    /** Se consume con la venta (la fila ya está bloqueada por el servicio). */
    public void usar(Long ventaId, Usuario usuario, Instant ahora) {
        if (usada()) {
            throw new IllegalStateException("La autorización ya se usó.");
        }
        this.ventaId = Objects.requireNonNull(ventaId, "ventaId");
        this.usadaPor = Objects.requireNonNull(usuario, "usuario");
        this.usadaEn = Objects.requireNonNull(ahora, "ahora");
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public BigDecimal getDescuentoMaximo() { return descuentoMaximo; }
    public boolean isPermiteBajoCosto() { return permiteBajoCosto; }
    public String getMotivo() { return motivo; }
    public Usuario getCreadaPor() { return creadaPor; }
    public Instant getCreadaEn() { return creadaEn; }
    public Instant getExpiraEn() { return expiraEn; }
    public Long getVentaId() { return ventaId; }
    public Usuario getUsadaPor() { return usadaPor; }
    public Instant getUsadaEn() { return usadaEn; }
}
