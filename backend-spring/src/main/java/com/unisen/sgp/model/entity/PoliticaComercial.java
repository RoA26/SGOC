package com.unisen.sgp.model.entity;

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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Política comercial de una empresa: límites de descuento por rol, tratamiento de las ventas
 * bajo el costo y margen objetivo para sugerir precios. Sin fila rigen {@link #POR_DEFECTO}.
 */
@Entity
@Table(name = "politicas_comerciales")
public class PoliticaComercial extends EntidadDeEmpresa {

    /**
     * Valores sin política aprobada: el trabajador no descuenta, el administrador comercial hasta
     * el 100 %, las ventas bajo el costo se permiten con advertencia y no hay margen objetivo.
     */
    public static final Valores POR_DEFECTO = new Valores(BigDecimal.ZERO.setScale(2), BigDecimal.valueOf(100).setScale(2),
            VentaBajoCosto.PERMITIR, null);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "descuento_max_trabajador", nullable = false, precision = 5, scale = 2)
    private BigDecimal descuentoMaxTrabajador;

    @Column(name = "descuento_max_administrador", nullable = false, precision = 5, scale = 2)
    private BigDecimal descuentoMaxAdministrador;

    @Enumerated(EnumType.STRING)
    @Column(name = "venta_bajo_costo", nullable = false, length = 15)
    private VentaBajoCosto ventaBajoCosto;

    @Column(name = "margen_objetivo", precision = 5, scale = 2)
    private BigDecimal margenObjetivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actualizada_por_id", nullable = false)
    private Usuario actualizadaPor;

    @Column(name = "actualizada_en", nullable = false)
    private Instant actualizadaEn;

    protected PoliticaComercial() {
    }

    public PoliticaComercial(Valores valores, Usuario usuario, Instant ahora) {
        asignar(valores, usuario, ahora);
    }

    public void actualizar(Valores valores, Usuario usuario, Instant ahora) {
        asignar(valores, usuario, ahora);
    }

    private void asignar(Valores valores, Usuario usuario, Instant ahora) {
        if (valores.descuentoMaxTrabajador().compareTo(valores.descuentoMaxAdministrador()) > 0) {
            throw new IllegalArgumentException("El límite del trabajador no puede superar el del administrador.");
        }
        this.descuentoMaxTrabajador = valores.descuentoMaxTrabajador();
        this.descuentoMaxAdministrador = valores.descuentoMaxAdministrador();
        this.ventaBajoCosto = Objects.requireNonNull(valores.ventaBajoCosto(), "ventaBajoCosto");
        this.margenObjetivo = valores.margenObjetivo();
        this.actualizadaPor = Objects.requireNonNull(usuario, "usuario");
        this.actualizadaEn = Objects.requireNonNull(ahora, "ahora");
    }

    public Valores valores() {
        return new Valores(descuentoMaxTrabajador, descuentoMaxAdministrador, ventaBajoCosto, margenObjetivo);
    }

    /** Valores de la política (los de la fila o los de por defecto). */
    public record Valores(BigDecimal descuentoMaxTrabajador, BigDecimal descuentoMaxAdministrador,
                          VentaBajoCosto ventaBajoCosto, BigDecimal margenObjetivo) {

        /** Límite de descuento efectivo por línea para el rol de quien vende. */
        public BigDecimal limitePara(Rol rol) {
            return rol == Rol.ADMINISTRADOR_COMERCIAL ? descuentoMaxAdministrador : descuentoMaxTrabajador;
        }
    }

    public Long getId() { return id; }
    public Usuario getActualizadaPor() { return actualizadaPor; }
    public Instant getActualizadaEn() { return actualizadaEn; }
}
