package com.unisen.sgp.model.entity;

import com.unisen.sgp.exception.ConflictoException;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Orden de compra a un proveedor: raíz del agregado cabecera-detalle. Sus líneas
 * ({@link DetalleOrdenCompra}) solo cambian mientras está en BORRADOR y siempre en la misma
 * transacción que la cabecera. Las transiciones de estado se validan aquí
 * ({@link EstadoOrdenCompra}); los servicios bloquean la fila antes de aplicarlas.
 */
@Entity
@Table(name = "ordenes_compra")
public class OrdenCompra extends EntidadAuditable {

    public static final int OBSERVACIONES_MAX_LENGTH = 1000;
    public static final int MOTIVO_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Correlativo por empresa (uq_ordenes_compra_numero); se muestra como OC-00001. */
    @Column(nullable = false, updatable = false)
    private long numero;

    /** Referencia de solo lectura: la orden se sigue leyendo aunque el proveedor se dé de baja. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id", nullable = false, updatable = false)
    private ProveedorReferencia proveedor;

    /** Solicitud aprobada de la que procede, si la hay (FK compuesta en la BD). */
    @Column(name = "solicitud_id", updatable = false)
    private Long solicitudId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private EstadoOrdenCompra estado = EstadoOrdenCompra.BORRADOR;

    @Column(length = OBSERVACIONES_MAX_LENGTH)
    private String observaciones;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creado_por_id", nullable = false, updatable = false)
    private Usuario creadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emitida_por_id")
    private Usuario emitidaPor;

    @Column(name = "emitida_en")
    private Instant emitidaEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelada_por_id")
    private Usuario canceladaPor;

    @Column(name = "cancelada_en")
    private Instant canceladaEn;

    @Column(name = "motivo_cancelacion", length = MOTIVO_MAX_LENGTH)
    private String motivoCancelacion;

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<DetalleOrdenCompra> detalles = new ArrayList<>();

    /** Requerido por JPA. */
    protected OrdenCompra() {
    }

    /** Toda orden nace en BORRADOR. */
    public OrdenCompra(long numero, ProveedorReferencia proveedor, Long solicitudId, String observaciones,
                       Usuario creadoPor) {
        if (numero <= 0) {
            throw new IllegalArgumentException("El número de orden debe ser positivo.");
        }
        this.numero = numero;
        this.proveedor = Objects.requireNonNull(proveedor, "proveedor");
        this.solicitudId = solicitudId;
        this.observaciones = observaciones;
        this.creadoPor = Objects.requireNonNull(creadoPor, "creadoPor");
    }

    /** Producto, cantidad y costo unitario de una línea (ya validados por el servicio). */
    public record Linea(ProductoReferencia producto, int cantidad, BigDecimal costoUnitario) {
    }

    /**
     * Fija las líneas del borrador. Las de un producto que ya estaba se actualizan en su sitio
     * (Hibernate inserta antes de borrar: sustituirlas chocaría con uq_detalles_orden_compra_producto),
     * las nuevas se añaden y las que faltan se eliminan.
     *
     * @throws ConflictoException si la orden ya no es un borrador
     */
    public void definirLineas(List<Linea> lineas) {
        exigirBorrador("modificar sus líneas");
        Map<Long, Linea> pendientes = new LinkedHashMap<>();
        for (Linea linea : lineas) {
            if (pendientes.put(linea.producto().getId(), linea) != null) {
                throw new IllegalArgumentException("Producto repetido en las líneas de la orden.");
            }
        }
        detalles.removeIf(detalle -> !pendientes.containsKey(detalle.getProducto().getId()));
        for (DetalleOrdenCompra detalle : detalles) {
            Linea linea = pendientes.remove(detalle.getProducto().getId());
            detalle.actualizar(linea.cantidad(), linea.costoUnitario());
        }
        pendientes.values().forEach(linea ->
                detalles.add(new DetalleOrdenCompra(this, linea.producto(), linea.cantidad(), linea.costoUnitario())));
    }

    /** @throws ConflictoException si la orden ya no es un borrador */
    public void cambiarObservaciones(String observaciones) {
        exigirBorrador("modificarla");
        this.observaciones = observaciones;
    }

    /**
     * Envía la orden al proveedor: desde aquí sus líneas no cambian.
     *
     * @throws ConflictoException si no está en BORRADOR o no tiene líneas
     */
    public void emitir(Usuario emisor, Instant ahora) {
        if (estado != EstadoOrdenCompra.BORRADOR) {
            throw new ConflictoException("Solo se emiten órdenes en borrador; esta está " + estado.descripcion() + ".");
        }
        if (detalles.isEmpty()) {
            throw new ConflictoException("La orden no tiene líneas: añade al menos un producto antes de emitirla.");
        }
        this.estado = EstadoOrdenCompra.EMITIDA;
        this.emitidaPor = Objects.requireNonNull(emisor, "emisor");
        this.emitidaEn = Objects.requireNonNull(ahora, "ahora");
    }

    /**
     * Cancela un borrador o una orden emitida que aún no ha recibido mercancía.
     *
     * @throws ConflictoException en cualquier otro estado
     */
    public void cancelar(Usuario usuario, String motivo, Instant ahora) {
        if (estado != EstadoOrdenCompra.BORRADOR && estado != EstadoOrdenCompra.EMITIDA) {
            String detalle = estado == EstadoOrdenCompra.RECIBIDA_PARCIALMENTE
                    ? " Ya tiene mercancía recibida, que sigue en el inventario."
                    : "";
            throw new ConflictoException("No se puede cancelar una orden " + estado.descripcion() + "." + detalle);
        }
        this.estado = EstadoOrdenCompra.CANCELADA;
        this.canceladaPor = Objects.requireNonNull(usuario, "usuario");
        this.canceladaEn = Objects.requireNonNull(ahora, "ahora");
        this.motivoCancelacion = Objects.requireNonNull(motivo, "motivo");
    }

    /** @throws ConflictoException si la orden no está emitida o parcialmente recibida */
    public void exigirQueAdmitaRecepcion() {
        if (!estado.admiteRecepcion()) {
            String motivo = estado == EstadoOrdenCompra.BORRADOR
                    ? "Emite la orden antes de registrar recepciones."
                    : "La orden está " + estado.descripcion() + ".";
            throw new ConflictoException("No se puede registrar mercancía en esta orden. " + motivo);
        }
    }

    /** Tras registrar lo recibido en las líneas: RECIBIDA si ya no falta nada, si no RECIBIDA_PARCIALMENTE. */
    public void actualizarEstadoPorRecepcion() {
        exigirQueAdmitaRecepcion();
        boolean completa = detalles.stream().allMatch(DetalleOrdenCompra::estaCompleta);
        this.estado = completa ? EstadoOrdenCompra.RECIBIDA : EstadoOrdenCompra.RECIBIDA_PARCIALMENTE;
    }

    /** Valor de la orden: suma de cantidad × costo unitario de sus líneas. */
    public BigDecimal total() {
        return detalles.stream().map(DetalleOrdenCompra::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void exigirBorrador(String accion) {
        if (estado != EstadoOrdenCompra.BORRADOR) {
            throw new ConflictoException("Solo se puede " + accion + " mientras la orden está en borrador; esta está "
                    + estado.descripcion() + ".");
        }
    }

    public Long getId() {
        return id;
    }

    public long getNumero() {
        return numero;
    }

    public ProveedorReferencia getProveedor() {
        return proveedor;
    }

    public Long getSolicitudId() {
        return solicitudId;
    }

    public EstadoOrdenCompra getEstado() {
        return estado;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public Usuario getCreadoPor() {
        return creadoPor;
    }

    public Usuario getEmitidaPor() {
        return emitidaPor;
    }

    public Instant getEmitidaEn() {
        return emitidaEn;
    }

    public Usuario getCanceladaPor() {
        return canceladaPor;
    }

    public Instant getCanceladaEn() {
        return canceladaEn;
    }

    public String getMotivoCancelacion() {
        return motivoCancelacion;
    }

    /** Solo lectura: las líneas se fijan con {@link #definirLineas}. */
    public List<DetalleOrdenCompra> getDetalles() {
        return Collections.unmodifiableList(detalles);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OrdenCompra that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return OrdenCompra.class.hashCode();
    }

    @Override
    public String toString() {
        return "OrdenCompra{id=" + id + ", numero=" + numero + ", estado=" + estado + '}';
    }
}
