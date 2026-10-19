package com.unisen.sgp.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Vista de solo lectura de la tabla {@code proveedores} para los documentos que la referencian
 * (órdenes de compra). Como {@link ProductoReferencia}, no lleva {@code @SQLRestriction}: una
 * orden histórica sigue mostrando su proveedor aunque se haya dado de baja después. Las altas,
 * cambios y bajas se hacen siempre a través de {@link Proveedor}.
 */
@Entity
@Immutable
@Table(name = "proveedores")
public class ProveedorReferencia extends EntidadDeEmpresa {

    @Id
    private Long id;

    @Column(nullable = false, insertable = false, updatable = false)
    private String nit;

    @Column(name = "razon_social", nullable = false, insertable = false, updatable = false)
    private String razonSocial;

    @Column(nullable = false, insertable = false, updatable = false)
    private boolean activo;

    /** Datos de contacto: los copia la instantánea del documento de la orden al emitirla. */
    @Column(insertable = false, updatable = false)
    private String email;

    @Column(insertable = false, updatable = false)
    private String telefono;

    @Column(insertable = false, updatable = false)
    private String direccion;

    /** Requerido por JPA. */
    protected ProveedorReferencia() {
    }

    public Long getId() {
        return id;
    }

    public String getNit() {
        return nit;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public boolean isActivo() {
        return activo;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getDireccion() {
        return direccion;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProveedorReferencia that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return ProveedorReferencia.class.hashCode();
    }

    @Override
    public String toString() {
        return "ProveedorReferencia{id=" + id + ", activo=" + activo + '}';
    }
}
