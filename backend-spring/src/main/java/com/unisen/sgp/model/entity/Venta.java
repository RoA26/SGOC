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
 * Venta confirmada. Sus importes y líneas son una copia histórica de lo calculado por el
 * servidor al confirmar: no cambian aunque cambien después los precios, el IVA o los costos.
 * Solo cambia el estado (devoluciones, anulación), y cada cambio deja su propio registro.
 */
@Entity
@Table(name = "ventas")
public class Venta extends EntidadDeEmpresa {

    public static final int MOTIVO_MAX_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private long numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private EstadoVenta estado = EstadoVenta.CONFIRMADA;

    @Column(name = "clave_idempotencia", nullable = false, updatable = false, length = 64)
    private String claveIdempotencia;

    @Column(name = "huella_solicitud", nullable = false, updatable = false, length = 64)
    private String huellaSolicitud;

    @Column(name = "cliente_nombre", updatable = false, length = 150)
    private String clienteNombre;

    @Column(name = "cliente_documento", updatable = false, length = 20)
    private String clienteDocumento;

    @Enumerated(EnumType.STRING)
    @Column(name = "descuento_general_tipo", updatable = false, length = 10)
    private TipoDescuento descuentoGeneralTipo;

    @Column(name = "descuento_general_valor", updatable = false, precision = 14, scale = 2)
    private BigDecimal descuentoGeneralValor;

    @Column(nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "descuento_lineas", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal descuentoLineas;

    @Column(name = "descuento_general", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal descuentoGeneral;

    @Column(name = "base_gravable", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal baseGravable;

    @Column(nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal impuestos;

    @Column(nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Column(name = "costo_total", updatable = false, precision = 14, scale = 2)
    private BigDecimal costoTotal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorizacion_id", updatable = false)
    private AutorizacionComercial autorizacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendida_por_id", nullable = false, updatable = false)
    private Usuario vendidaPor;

    @Column(name = "vendida_en", nullable = false, updatable = false)
    private Instant vendidaEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anulada_por_id")
    private Usuario anuladaPor;

    @Column(name = "anulada_en")
    private Instant anuladaEn;

    @Column(name = "motivo_anulacion", length = MOTIVO_MAX_LENGTH)
    private String motivoAnulacion;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.PERSIST)
    @OrderBy("linea ASC")
    private List<DetalleVenta> detalles = new ArrayList<>();

    @OneToMany(mappedBy = "venta", cascade = CascadeType.PERSIST)
    @OrderBy("id ASC")
    private List<PagoVenta> pagos = new ArrayList<>();

    protected Venta() {
    }

    /** Cabecera con los totales ya calculados; las líneas y los pagos se añaden después. */
    public Venta(long numero, String claveIdempotencia, String huellaSolicitud, String clienteNombre,
                 String clienteDocumento, TipoDescuento descuentoGeneralTipo, BigDecimal descuentoGeneralValor,
                 Totales totales, AutorizacionComercial autorizacion, Usuario vendidaPor, Instant vendidaEn) {
        this.numero = numero;
        this.claveIdempotencia = Objects.requireNonNull(claveIdempotencia, "claveIdempotencia");
        this.huellaSolicitud = Objects.requireNonNull(huellaSolicitud, "huellaSolicitud");
        this.clienteNombre = clienteNombre;
        this.clienteDocumento = clienteDocumento;
        this.descuentoGeneralTipo = descuentoGeneralTipo;
        this.descuentoGeneralValor = descuentoGeneralValor;
        this.subtotal = totales.subtotal();
        this.descuentoLineas = totales.descuentoLineas();
        this.descuentoGeneral = totales.descuentoGeneral();
        this.baseGravable = totales.baseGravable();
        this.impuestos = totales.impuestos();
        this.total = totales.total();
        this.costoTotal = totales.costoTotal();
        this.autorizacion = autorizacion;
        this.vendidaPor = Objects.requireNonNull(vendidaPor, "vendidaPor");
        this.vendidaEn = Objects.requireNonNull(vendidaEn, "vendidaEn");
    }

    public record Totales(BigDecimal subtotal, BigDecimal descuentoLineas, BigDecimal descuentoGeneral,
                          BigDecimal baseGravable, BigDecimal impuestos, BigDecimal total, BigDecimal costoTotal) {
    }

    public void agregarLinea(DetalleVenta linea) {
        linea.asignarVenta(this);
        detalles.add(linea);
    }

    public void agregarPago(PagoVenta pago) {
        pago.asignarVenta(this);
        pagos.add(pago);
    }

    /** Recalcula el estado tras una devolución a partir de lo devuelto en cada línea. */
    public void actualizarEstadoPorDevolucion() {
        if (estado == EstadoVenta.ANULADA) {
            throw new IllegalStateException("Una venta anulada no admite devoluciones.");
        }
        boolean todo = detalles.stream().allMatch(d -> d.getCantidadDevuelta() == d.getCantidad());
        boolean algo = detalles.stream().anyMatch(d -> d.getCantidadDevuelta() > 0);
        estado = todo ? EstadoVenta.DEVUELTA : algo ? EstadoVenta.DEVUELTA_PARCIALMENTE : EstadoVenta.CONFIRMADA;
    }

    public void anular(Usuario usuario, String motivo, Instant ahora) {
        if (estado != EstadoVenta.CONFIRMADA) {
            throw new IllegalStateException("Solo se anula una venta confirmada sin devoluciones.");
        }
        this.estado = EstadoVenta.ANULADA;
        this.anuladaPor = Objects.requireNonNull(usuario, "usuario");
        this.motivoAnulacion = Objects.requireNonNull(motivo, "motivo");
        this.anuladaEn = Objects.requireNonNull(ahora, "ahora");
    }

    public Long getId() { return id; }
    public long getNumero() { return numero; }
    public EstadoVenta getEstado() { return estado; }
    public String getClaveIdempotencia() { return claveIdempotencia; }
    public String getHuellaSolicitud() { return huellaSolicitud; }
    public String getClienteNombre() { return clienteNombre; }
    public String getClienteDocumento() { return clienteDocumento; }
    public TipoDescuento getDescuentoGeneralTipo() { return descuentoGeneralTipo; }
    public BigDecimal getDescuentoGeneralValor() { return descuentoGeneralValor; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDescuentoLineas() { return descuentoLineas; }
    public BigDecimal getDescuentoGeneral() { return descuentoGeneral; }
    public BigDecimal getBaseGravable() { return baseGravable; }
    public BigDecimal getImpuestos() { return impuestos; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getCostoTotal() { return costoTotal; }
    public AutorizacionComercial getAutorizacion() { return autorizacion; }
    public Usuario getVendidaPor() { return vendidaPor; }
    public Instant getVendidaEn() { return vendidaEn; }
    public Usuario getAnuladaPor() { return anuladaPor; }
    public Instant getAnuladaEn() { return anuladaEn; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
    public List<DetalleVenta> getDetalles() { return detalles; }
    public List<PagoVenta> getPagos() { return pagos; }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Venta that && id != null && id.equals(that.getId()));
    }

    @Override
    public int hashCode() {
        return Venta.class.hashCode();
    }

    @Override
    public String toString() {
        return "Venta{id=" + id + ", numero=" + numero + ", estado=" + estado + '}';
    }
}
