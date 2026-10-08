package com.unisen.sgp.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Empresa cliente del SaaS (tenant). Todos los datos de negocio (proveedores, productos,
 * solicitudes…) llevan su {@code empresa_id}; ver {@link EntidadDeEmpresa}.
 *
 * <p>No es una entidad "de empresa": la tabla de tenants es global y solo la gestiona el
 * SUPER_ADMIN.
 */
@Entity
@Table(name = "empresas")
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(nullable = false, unique = true, length = 20)
    private String nit;

    /** Una empresa inactiva bloquea el acceso de todos sus usuarios. */
    @Column(nullable = false)
    private boolean activa = true;

    /** Código para unirse a la empresa; reservado para el alta de usuarios por empresa. */
    @Column(name = "codigo_invitacion_actual", unique = true, length = 32)
    private String codigoInvitacionActual;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Requerido por JPA. */
    protected Empresa() {
    }

    public Empresa(String nombre, String nit) {
        this.nombre = Objects.requireNonNull(nombre, "nombre").strip();
        this.nit = Objects.requireNonNull(nit, "nit").strip();
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getNit() {
        return nit;
    }

    public boolean isActiva() {
        return activa;
    }

    public void setActiva(boolean activa) {
        this.activa = activa;
    }

    public String getCodigoInvitacionActual() {
        return codigoInvitacionActual;
    }

    public void setCodigoInvitacionActual(String codigoInvitacionActual) {
        this.codigoInvitacionActual = codigoInvitacionActual;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Empresa that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Empresa.class.hashCode();
    }

    @Override
    public String toString() {
        return "Empresa{id=" + id + ", nit='" + nit + "', activa=" + activa + '}';
    }
}
