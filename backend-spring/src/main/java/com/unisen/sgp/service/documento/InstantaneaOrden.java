package com.unisen.sgp.service.documento;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Lo que se emitió, congelado: la base del documento Word. Se guarda en JSON
 * ({@code documentos_orden_compra.contenido}) y no se recalcula nunca a partir del catálogo.
 *
 * <p>Solo contiene datos que existen en el sistema: la empresa no tiene dirección ni logo, los
 * productos no tienen unidad de medida y no hay impuestos ni descuentos aprobados, así que el
 * documento no los muestra (no se inventan).
 *
 * @param formato      versión del formato de esta instantánea (para leer las antiguas si cambia)
 * @param reconstruida true si la orden se emitió antes de que existieran los documentos (V13) y
 *                     la instantánea se armó al descargarla con los datos vigentes
 */
public record InstantaneaOrden(
        int formato,
        Orden orden,
        Comprador comprador,
        Proveedor proveedor,
        Entrega entrega,
        List<Linea> lineas,
        BigDecimal total,
        String moneda,
        Autorizacion autorizacion,
        boolean reconstruida) {

    public static final int FORMATO_ACTUAL = 1;
    public static final String MONEDA = "COP";

    public record Orden(long id, long numero, String numeroVisible, Instant emitidaEn, Long solicitudId,
                        String observaciones) {
    }

    public record Comprador(String nombre, String nit, String codigo) {
    }

    public record Proveedor(String nit, String razonSocial, String email, String telefono, String direccion) {
    }

    /** Programación vigente al emitir; null si aún no se había programado. */
    public record Entrega(LocalDate fechaEstimada, LocalTime horaDesde, LocalTime horaHasta,
                          boolean confirmadaProveedor, String referenciaProveedor, String observaciones) {
    }

    public record Linea(int numero, String codigo, String codigoBarras, String descripcion, int cantidad,
                        BigDecimal precioUnitario, BigDecimal subtotal) {
    }

    /** Quién emitió la orden en el sistema y con qué rol (el modelo no tiene cargos). */
    public record Autorizacion(String nombre, String rol) {
    }
}
