package com.unisen.sgp.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Columnas de auditoría comunes a las entidades de catálogo, que siempre son de una empresa. */
@MappedSuperclass
public abstract class EntidadAuditable extends EntidadDeEmpresa {

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Un único instante para ambas columnas: en el alta coinciden exactamente. */
    @PrePersist
    void alCrear() {
        Instant ahora = ahora();
        creadoEn = ahora;
        actualizadoEn = ahora;
    }

    @PreUpdate
    void alActualizar() {
        actualizadoEn = ahora();
    }

    /** Precisión de microsegundos, la de TIMESTAMP en PostgreSQL: la respuesta coincide con lo guardado. */
    private static Instant ahora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
