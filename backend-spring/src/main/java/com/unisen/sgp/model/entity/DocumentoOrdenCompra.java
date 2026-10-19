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
import java.util.Objects;
import org.hibernate.annotations.Immutable;

/**
 * Versión documental de una orden emitida: la instantánea (JSON) de lo que se envió al
 * proveedor y su huella SHA-256. El .docx se genera desde aquí en cada descarga, así el
 * documento es siempre el de la emisión aunque cambien el catálogo o la empresa. Inmutable.
 */
@Entity
@Immutable
@Table(name = "documentos_orden_compra")
public class DocumentoOrdenCompra extends EntidadDeEmpresa {

    /** Motivo de la versión. Hoy solo la emisión: una orden emitida no se modifica. */
    public static final String MOTIVO_EMISION = "EMISION";
    public static final int CONTENIDO_MAX_LENGTH = 200_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_id", nullable = false, updatable = false)
    private OrdenCompra orden;

    @Column(nullable = false, updatable = false)
    private int version;

    @Column(nullable = false, updatable = false, length = 20)
    private String motivo;

    @Column(nullable = false, updatable = false, length = CONTENIDO_MAX_LENGTH)
    private String contenido;

    @Column(name = "huella_sha256", nullable = false, updatable = false, length = 64)
    private String huellaSha256;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generado_por_id", nullable = false, updatable = false)
    private Usuario generadoPor;

    @Column(name = "generado_en", nullable = false, updatable = false)
    private Instant generadoEn;

    /** Requerido por JPA. */
    protected DocumentoOrdenCompra() {
    }

    public DocumentoOrdenCompra(OrdenCompra orden, int version, String motivo, String contenido, String huellaSha256,
                                Usuario generadoPor, Instant generadoEn) {
        if (version <= 0) {
            throw new IllegalArgumentException("Versión no válida: " + version);
        }
        if (contenido.length() > CONTENIDO_MAX_LENGTH) {
            throw new IllegalArgumentException("La instantánea supera " + CONTENIDO_MAX_LENGTH + " caracteres.");
        }
        this.orden = Objects.requireNonNull(orden, "orden");
        this.version = version;
        this.motivo = Objects.requireNonNull(motivo, "motivo");
        this.contenido = contenido;
        this.huellaSha256 = Objects.requireNonNull(huellaSha256, "huellaSha256");
        this.generadoPor = Objects.requireNonNull(generadoPor, "generadoPor");
        this.generadoEn = Objects.requireNonNull(generadoEn, "generadoEn");
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

    public String getMotivo() {
        return motivo;
    }

    public String getContenido() {
        return contenido;
    }

    public String getHuellaSha256() {
        return huellaSha256;
    }

    public Usuario getGeneradoPor() {
        return generadoPor;
    }

    public Instant getGeneradoEn() {
        return generadoEn;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DocumentoOrdenCompra that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return DocumentoOrdenCompra.class.hashCode();
    }

    @Override
    public String toString() {
        return "DocumentoOrdenCompra{id=" + id + ", version=" + version + '}';
    }
}
