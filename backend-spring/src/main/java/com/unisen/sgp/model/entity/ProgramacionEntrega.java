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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import org.hibernate.annotations.Immutable;

/**
 * Una versión de la programación de la entrega de una orden. Es un hecho del historial: cada
 * cambio crea una versión nueva y la orden apunta a la vigente
 * ({@link OrdenCompra#getProgramacionEntrega()}), así el calendario muestra la fecha actual y
 * la trazabilidad conserva las anteriores.
 *
 * <p>La fecha estimada es lo que planifica el gerente; {@code confirmadaProveedor} indica que el
 * proveedor la confirmó. Ninguna de las dos es la recepción real ({@link Recepcion#getRecibidaEn()}).
 */
@Entity
@Immutable
@Table(name = "programaciones_entrega")
public class ProgramacionEntrega extends EntidadDeEmpresa {

    public static final int REFERENCIA_MAX_LENGTH = 60;
    public static final int OBSERVACIONES_MAX_LENGTH = 500;
    public static final int MOTIVO_MAX_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_id", nullable = false, updatable = false)
    private OrdenCompra orden;

    @Column(nullable = false, updatable = false)
    private int version;

    @Column(name = "fecha_estimada", nullable = false, updatable = false)
    private LocalDate fechaEstimada;

    @Column(name = "hora_desde", updatable = false)
    private LocalTime horaDesde;

    @Column(name = "hora_hasta", updatable = false)
    private LocalTime horaHasta;

    @Column(name = "confirmada_proveedor", nullable = false, updatable = false)
    private boolean confirmadaProveedor;

    @Column(name = "referencia_proveedor", updatable = false, length = REFERENCIA_MAX_LENGTH)
    private String referenciaProveedor;

    @Column(updatable = false, length = OBSERVACIONES_MAX_LENGTH)
    private String observaciones;

    @Column(name = "motivo_cambio", updatable = false, length = MOTIVO_MAX_LENGTH)
    private String motivoCambio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registrada_por_id", nullable = false, updatable = false)
    private Usuario registradaPor;

    @Column(name = "registrada_en", nullable = false, updatable = false)
    private Instant registradaEn;

    /** Requerido por JPA. */
    protected ProgramacionEntrega() {
    }

    /** Datos de una programación, ya validados en formato por el DTO. */
    public record Datos(LocalDate fechaEstimada, LocalTime horaDesde, LocalTime horaHasta, boolean confirmadaProveedor,
                        String referenciaProveedor, String observaciones) {

        public Datos {
            Objects.requireNonNull(fechaEstimada, "fechaEstimada");
            if ((horaDesde == null) != (horaHasta == null)) {
                throw new IllegalArgumentException("La franja necesita hora inicial y final.");
            }
            if (horaDesde != null && !horaDesde.isBefore(horaHasta)) {
                throw new IllegalArgumentException("La hora inicial debe ser anterior a la final.");
            }
        }

        /** Sin franja, la entrega es "todo el día": nunca se inventa una hora. */
        public boolean tieneFranja() {
            return horaDesde != null;
        }
    }

    /** Solo la crea {@link OrdenCompra#programarEntrega}, que decide la versión. */
    ProgramacionEntrega(OrdenCompra orden, int version, Datos datos, String motivoCambio, Usuario registradaPor,
                        Instant registradaEn) {
        if (version <= 0) {
            throw new IllegalArgumentException("Versión no válida: " + version);
        }
        if (version > 1 && motivoCambio == null) {
            throw new IllegalArgumentException("Reprogramar exige un motivo.");
        }
        this.orden = Objects.requireNonNull(orden, "orden");
        this.version = version;
        this.fechaEstimada = datos.fechaEstimada();
        this.horaDesde = datos.horaDesde();
        this.horaHasta = datos.horaHasta();
        this.confirmadaProveedor = datos.confirmadaProveedor();
        this.referenciaProveedor = datos.referenciaProveedor();
        this.observaciones = datos.observaciones();
        this.motivoCambio = motivoCambio;
        this.registradaPor = Objects.requireNonNull(registradaPor, "registradaPor");
        this.registradaEn = Objects.requireNonNull(registradaEn, "registradaEn");
    }

    public Datos datos() {
        return new Datos(fechaEstimada, horaDesde, horaHasta, confirmadaProveedor, referenciaProveedor, observaciones);
    }

    public Long getId() {
        return id;
    }

    public OrdenCompra getOrden() {
        return orden;
    }

    public int getVersion() {
        return version;
    }

    public LocalDate getFechaEstimada() {
        return fechaEstimada;
    }

    public LocalTime getHoraDesde() {
        return horaDesde;
    }

    public LocalTime getHoraHasta() {
        return horaHasta;
    }

    public boolean isConfirmadaProveedor() {
        return confirmadaProveedor;
    }

    public String getReferenciaProveedor() {
        return referenciaProveedor;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public String getMotivoCambio() {
        return motivoCambio;
    }

    public Usuario getRegistradaPor() {
        return registradaPor;
    }

    public Instant getRegistradaEn() {
        return registradaEn;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProgramacionEntrega that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return ProgramacionEntrega.class.hashCode();
    }

    @Override
    public String toString() {
        return "ProgramacionEntrega{id=" + id + ", version=" + version + ", fecha=" + fechaEstimada + '}';
    }
}
