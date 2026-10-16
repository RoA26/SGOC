package com.unisen.sgp.model.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Entrada de mercancía de una orden emitida (parcial o total). Es un hecho: una vez
 * registrada no se modifica (columnas {@code updatable = false}; una corrección es un ajuste de
 * inventario). La clave de
 * idempotencia, única por empresa, evita registrarla dos veces si el cliente repite la petición.
 */
@Entity
@Table(name = "recepciones")
public class Recepcion extends EntidadDeEmpresa {

    public static final int CLAVE_MAX_LENGTH = 64;
    public static final int OBSERVACIONES_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_id", nullable = false, updatable = false)
    private OrdenCompra orden;

    @Column(name = "clave_idempotencia", nullable = false, updatable = false, length = CLAVE_MAX_LENGTH)
    private String claveIdempotencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recibida_por_id", nullable = false, updatable = false)
    private Usuario recibidaPor;

    @Column(name = "recibida_en", nullable = false, updatable = false)
    private Instant recibidaEn;

    @Column(updatable = false, length = OBSERVACIONES_MAX_LENGTH)
    private String observaciones;

    @OneToMany(mappedBy = "recepcion", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<DetalleRecepcion> detalles = new ArrayList<>();

    /** Requerido por JPA. */
    protected Recepcion() {
    }

    public Recepcion(OrdenCompra orden, String claveIdempotencia, Usuario recibidaPor, Instant recibidaEn,
                     String observaciones) {
        this.orden = Objects.requireNonNull(orden, "orden");
        this.claveIdempotencia = Objects.requireNonNull(claveIdempotencia, "claveIdempotencia");
        this.recibidaPor = Objects.requireNonNull(recibidaPor, "recibidaPor");
        this.recibidaEn = Objects.requireNonNull(recibidaEn, "recibidaEn");
        this.observaciones = observaciones;
    }

    /** Añade una línea y suma las unidades a la línea de la orden. */
    public DetalleRecepcion registrar(DetalleOrdenCompra lineaOrden, int cantidad) {
        if (!lineaOrden.getOrden().equals(orden)) {
            throw new IllegalArgumentException("La línea no pertenece a la orden de la recepción.");
        }
        lineaOrden.recibir(cantidad);
        DetalleRecepcion detalle = new DetalleRecepcion(this, lineaOrden, cantidad);
        detalles.add(detalle);
        return detalle;
    }

    public Long getId() {
        return id;
    }

    public OrdenCompra getOrden() {
        return orden;
    }

    public String getClaveIdempotencia() {
        return claveIdempotencia;
    }

    public Usuario getRecibidaPor() {
        return recibidaPor;
    }

    public Instant getRecibidaEn() {
        return recibidaEn;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public List<DetalleRecepcion> getDetalles() {
        return Collections.unmodifiableList(detalles);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Recepcion that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Recepcion.class.hashCode();
    }

    @Override
    public String toString() {
        return "Recepcion{id=" + id + ", lineas=" + detalles.size() + '}';
    }
}
