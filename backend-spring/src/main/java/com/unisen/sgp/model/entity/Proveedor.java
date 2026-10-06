package com.unisen.sgp.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Proveedor del catálogo de compras.
 *
 * <p>Borrado lógico: {@code repository.delete(...)} ejecuta el UPDATE de {@link SQLDelete} y
 * {@link SQLRestriction} oculta los inactivos en todas las consultas. ({@code @SQLRestriction}
 * es el reemplazo de {@code @Where}, deprecado desde Hibernate 6.3.)
 */
@Entity
@Table(name = "proveedores")
@SQLDelete(sql = "UPDATE proveedores SET activo = false, actualizado_en = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("activo = true")
public class Proveedor extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String nit;

    @Column(name = "razon_social", nullable = false, length = 200)
    private String razonSocial;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(length = 20)
    private String telefono;

    @Column(length = 300)
    private String direccion;

    @Column(nullable = false)
    private boolean activo = true;

    /** Requerido por JPA. */
    protected Proveedor() {
    }

    public Proveedor(String nit, String razonSocial, String email, String telefono, String direccion) {
        asignarDatos(nit, razonSocial, email, telefono, direccion);
    }

    public void actualizar(String nit, String razonSocial, String email, String telefono, String direccion) {
        asignarDatos(nit, razonSocial, email, telefono, direccion);
    }

    private void asignarDatos(String nit, String razonSocial, String email, String telefono, String direccion) {
        this.nit = Objects.requireNonNull(nit, "nit");
        this.razonSocial = Objects.requireNonNull(razonSocial, "razonSocial");
        this.email = Objects.requireNonNull(email, "email");
        this.telefono = telefono;
        this.direccion = direccion;
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

    public String getEmail() {
        return email;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getDireccion() {
        return direccion;
    }

    public boolean isActivo() {
        return activo;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Proveedor that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Proveedor.class.hashCode();
    }

    @Override
    public String toString() {
        return "Proveedor{id=" + id + ", nit='" + nit + "', activo=" + activo + '}';
    }
}
