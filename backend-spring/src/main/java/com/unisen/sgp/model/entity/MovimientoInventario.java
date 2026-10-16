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
 * Fila del kárdex: un cambio de existencias con su origen, quién, cuándo, cuánto (con signo) y
 * el saldo resultante. Inmutable: los errores se corrigen con otro movimiento (un ajuste).
 *
 * <p>El costo unitario es el factual de la entrada (el de la orden en una recepción, el
 * declarado en un ajuste de entrada). No se calcula aquí ningún costo promedio: la estrategia de
 * valoración está documentada como decisión pendiente (docs/auditoria-sgp.md).
 */
@Entity
@Immutable
@Table(name = "movimientos_inventario")
public class MovimientoInventario extends EntidadDeEmpresa {

    public static final int MOTIVO_MAX_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false, updatable = false)
    private ProductoReferencia producto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TipoMovimiento tipo;

    @Column(nullable = false, updatable = false)
    private int cantidad;

    @Column(name = "saldo_resultante", nullable = false, updatable = false)
    private int saldoResultante;

    @Column(name = "costo_unitario", updatable = false, precision = 14, scale = 2)
    private BigDecimal costoUnitario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detalle_recepcion_id", updatable = false)
    private DetalleRecepcion detalleRecepcion;

    @Column(updatable = false, length = MOTIVO_MAX_LENGTH)
    private String motivo;

    @Column(name = "clave_idempotencia", updatable = false, length = Recepcion.CLAVE_MAX_LENGTH)
    private String claveIdempotencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(nullable = false, updatable = false)
    private Instant fecha;

    /** Requerido por JPA. */
    protected MovimientoInventario() {
    }

    private MovimientoInventario(ProductoReferencia producto, TipoMovimiento tipo, int cantidad, int saldoResultante,
                                 BigDecimal costoUnitario, Usuario usuario, Instant fecha) {
        if (cantidad == 0 || saldoResultante < 0) {
            throw new IllegalArgumentException("Movimiento de inventario no válido.");
        }
        this.producto = Objects.requireNonNull(producto, "producto");
        this.tipo = tipo;
        this.cantidad = cantidad;
        this.saldoResultante = saldoResultante;
        this.costoUnitario = costoUnitario;
        this.usuario = Objects.requireNonNull(usuario, "usuario");
        this.fecha = Objects.requireNonNull(fecha, "fecha");
    }

    /** Entrada por una línea de recepción, con el costo pactado en la orden. */
    public static MovimientoInventario deRecepcion(DetalleRecepcion linea, int saldoResultante, Usuario usuario,
                                                   Instant fecha) {
        DetalleOrdenCompra lineaOrden = linea.getDetalleOrden();
        MovimientoInventario movimiento = new MovimientoInventario(lineaOrden.getProducto(), TipoMovimiento.RECEPCION,
                linea.getCantidad(), saldoResultante, lineaOrden.getCostoUnitario(), usuario, fecha);
        movimiento.detalleRecepcion = linea;
        return movimiento;
    }

    /** Corrección manual: con motivo y la clave de idempotencia de la petición. */
    public static MovimientoInventario deAjuste(ProductoReferencia producto, int cantidad, int saldoResultante,
                                                BigDecimal costoUnitario, String motivo, String claveIdempotencia,
                                                Usuario usuario, Instant fecha) {
        MovimientoInventario movimiento = new MovimientoInventario(producto, TipoMovimiento.AJUSTE, cantidad,
                saldoResultante, costoUnitario, usuario, fecha);
        movimiento.motivo = Objects.requireNonNull(motivo, "motivo");
        movimiento.claveIdempotencia = Objects.requireNonNull(claveIdempotencia, "claveIdempotencia");
        return movimiento;
    }

    public Long getId() {
        return id;
    }

    public ProductoReferencia getProducto() {
        return producto;
    }

    public TipoMovimiento getTipo() {
        return tipo;
    }

    public int getCantidad() {
        return cantidad;
    }

    public int getSaldoResultante() {
        return saldoResultante;
    }

    public BigDecimal getCostoUnitario() {
        return costoUnitario;
    }

    public DetalleRecepcion getDetalleRecepcion() {
        return detalleRecepcion;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getClaveIdempotencia() {
        return claveIdempotencia;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Instant getFecha() {
        return fecha;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MovimientoInventario that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return MovimientoInventario.class.hashCode();
    }

    @Override
    public String toString() {
        return "MovimientoInventario{id=" + id + ", tipo=" + tipo + ", cantidad=" + cantidad + '}';
    }
}
