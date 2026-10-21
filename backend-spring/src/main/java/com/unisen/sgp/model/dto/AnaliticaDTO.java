package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.EstadoOrdenCompra;
import com.unisen.sgp.model.entity.EstadoSolicitud;
import com.unisen.sgp.model.entity.EstadoStock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Respuestas de la analítica: cifras ya agregadas en la BD, sin filas de detalle. Los conteos
 * por estado incluyen todos los estados (0 si no hay ninguno). {@code totalHistorico} permite a
 * la interfaz distinguir "aún no hay datos" (0 histórico) de "ninguno en este período".
 */
public final class AnaliticaDTO {

    private AnaliticaDTO() {
    }

    public enum Granularidad {
        DIA,
        MES
    }

    /** Fechas locales de la zona del negocio, ambas incluidas. */
    public record Periodo(LocalDate desde, LocalDate hasta, String zonaHoraria, Granularidad granularidad) {
    }

    /** Un punto de una serie: el día (o el primer día del mes) y su valor. */
    public record Punto(LocalDate fecha, long valor) {
    }

    // --- SUPER_ADMIN (modo global) ---

    /**
     * Empresas que cuenta la analítica global: las reales (por defecto), solo las de demostración
     * ({@code empresas.demo}) o todas. Así las cifras de la demo no se mezclan con las reales.
     */
    public enum AlcanceEmpresas {
        REALES(false),
        DEMO(true),
        TODAS(null);

        private final Boolean demo;

        AlcanceEmpresas(Boolean demo) {
            this.demo = demo;
        }

        /** Valor de {@code empresas.demo} que se exige; null = sin filtro. */
        public Boolean demo() {
            return demo;
        }
    }

    public record Empresas(long total, long activas, long inactivas) {
    }

    public record OrdenesGlobales(long emitidasEnPeriodo, long totalHistorico, Map<EstadoOrdenCompra, Long> porEstado) {
    }

    /** Empresa con más órdenes emitidas en el período; el valor excluye las canceladas. */
    public record EmpresaConActividad(Long id, String nombre, String codigo, boolean demo, long ordenes,
                                      BigDecimal valorComprometido) {
    }

    /** @param alcance empresas que entran en todas las cifras de la respuesta */
    public record Global(AlcanceEmpresas alcance, Periodo periodo, Empresas empresas, OrdenesGlobales ordenes,
                         List<EmpresaConActividad> topEmpresas, List<Punto> evolucionOrdenes) {
    }

    /** Fila del listado paginado «órdenes por empresa». */
    public record OrdenesDeEmpresa(Long id, String nombre, String codigo, boolean activa, boolean demo,
                                   long ordenesEnPeriodo, long ordenesAbiertas) {
    }

    // --- ADMINISTRADOR_COMERCIAL (o SUPER_ADMIN en supervisión) ---

    public record ProductoEnAlerta(Long id, String sku, String nombre, int cantidad, int stockMinimo, EstadoStock estado) {
    }

    public record Inventario(long productosActivos, long disponibles, long agotados, long criticos,
                             List<ProductoEnAlerta> requierenAtencion) {
    }

    /**
     * Compras del período: órdenes emitidas en él y su valor (sin las canceladas), y el valor de
     * la mercancía recibida en él al costo pactado. Los estados son la foto actual.
     */
    public record Compras(long ordenesEmitidas, BigDecimal valorComprometido, BigDecimal valorRecibido,
                          long totalHistorico, Map<EstadoOrdenCompra, Long> ordenesPorEstado) {
    }

    public record Recepciones(long enPeriodo, long unidades, long totalHistorico, List<Punto> evolucion) {
    }

    public record DeEmpresa(Periodo periodo, Inventario inventario, Map<EstadoSolicitud, Long> solicitudesPorEstado,
                            Compras compras, Recepciones recepciones) {
    }

    // --- TRABAJADOR ---

    /** Sin indicadores de ventas: no existen ventas reales hasta que se implemente el POS. */
    public record Personal(long productosDisponibles, long productosCriticos, long productosAgotados,
                           Map<EstadoSolicitud, Long> misSolicitudes) {
    }
}
