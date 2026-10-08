package com.unisen.sgp.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.TenantId;

/**
 * Base de las entidades que pertenecen a una empresa (tenant).
 *
 * <p>{@link TenantId} activa la multi-empresa nativa de Hibernate 6 (discriminador en esquema
 * compartido): cada consulta, incluida la carga por id, añade {@code empresa_id = <tenant
 * actual>} y cada alta recibe automáticamente la empresa del contexto
 * ({@code TenantIdentifierResolver}). El código de negocio nunca asigna ni filtra la empresa
 * a mano.
 *
 * <p>La misma columna se mapea dos veces: {@code empresaId} (la que escribe Hibernate) y
 * {@code empresa}, una asociación de solo lectura para navegar hasta la entidad.
 */
@MappedSuperclass
public abstract class EntidadDeEmpresa {

    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", insertable = false, updatable = false)
    private Empresa empresa;

    public Long getEmpresaId() {
        return empresaId;
    }

    /** Asociación LAZY: solo dentro de una transacción. */
    public Empresa getEmpresa() {
        return empresa;
    }
}
