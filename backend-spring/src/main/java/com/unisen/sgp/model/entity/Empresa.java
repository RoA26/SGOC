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

    /**
     * Código empresarial: identificador legible, único y estable de la organización (p. ej.
     * {@code NASC01}). Es público: no es una credencial ni concede ningún permiso. Formato en
     * {@code Patrones.CODIGO_EMPRESARIAL} (restricción {@code ck_empresas_codigo}).
     */
    @Column(nullable = false, unique = true, updatable = false, length = 12)
    private String codigo;

    /** Una empresa inactiva bloquea el acceso de todos sus usuarios. */
    @Column(nullable = false)
    private boolean activa = true;

    /** Empresa ficticia creada por la seed de demostración (perfil demo); nunca una real. */
    @Column(nullable = false)
    private boolean demo;

    /**
     * Código de registro de trabajadores: aleatorio y secreto (80 bits); quien lo conoce puede
     * registrarse en esta empresa y queda PENDIENTE hasta que el administrador lo apruebe. Único y
     * estable: no caduca ni se consume, a diferencia de un {@link CodigoInvitacion}. No debe
     * confundirse con {@link #codigo}, el identificador público de la empresa.
     */
    @Column(name = "codigo_empresa", nullable = false, unique = true, updatable = false, length = 32)
    private String codigoEmpresa;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Requerido por JPA. */
    protected Empresa() {
    }

    /**
     * @param codigo        código empresarial ya normalizado y único (ver {@code CodigosEmpresariales})
     * @param codigoEmpresa código de registro de trabajadores, ya normalizado (ver
     *                      {@code CodigosInvitacion}) y único
     */
    public Empresa(String nombre, String nit, String codigo, String codigoEmpresa) {
        this.nombre = Objects.requireNonNull(nombre, "nombre").strip();
        this.nit = Objects.requireNonNull(nit, "nit").strip();
        this.codigo = Objects.requireNonNull(codigo, "codigo");
        this.codigoEmpresa = Objects.requireNonNull(codigoEmpresa, "codigoEmpresa");
    }

    public Long getId() {
        return id;
    }

    public boolean isDemo() {
        return demo;
    }

    /** Solo la seed de demostración, al crear la empresa. */
    public void marcarDemo() {
        this.demo = true;
    }

    public String getNombre() {
        return nombre;
    }

    public String getNit() {
        return nit;
    }

    public String getCodigo() {
        return codigo;
    }

    public boolean isActiva() {
        return activa;
    }

    public void setActiva(boolean activa) {
        this.activa = activa;
    }

    public String getCodigoEmpresa() {
        return codigoEmpresa;
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
        return "Empresa{id=" + id + ", codigo='" + codigo + "', nit='" + nit + "', activa=" + activa + '}';
    }
}
