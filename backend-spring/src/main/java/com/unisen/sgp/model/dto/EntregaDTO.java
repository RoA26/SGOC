package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.EstadoOrdenCompra;
import com.unisen.sgp.model.entity.OrdenCompra;
import com.unisen.sgp.model.entity.SituacionEntrega;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Entrega de una orden emitida, sin costos (la consultan también los trabajadores).
 *
 * @param programacion  programación vigente (fecha estimada; no es la recepción real), o null
 * @param diasRetraso   días desde la fecha estimada, si está retrasada; 0 en otro caso
 * @param alertas       motivos por los que necesita intervención (RETRASADA, SIN_PROGRAMAR, INCIDENCIAS)
 */
public record EntregaDTO(
        Long ordenId,
        long numero,
        EstadoOrdenCompra estadoOrden,
        SituacionEntrega situacion,
        Proveedor proveedor,
        Instant emitidaEn,
        ProgramacionEntregaDTO programacion,
        int diasRetraso,
        int unidadesPedidas,
        int unidadesRecibidas,
        int unidadesPendientes,
        long recepciones,
        Instant ultimaRecepcionEn,
        long incidencias,
        List<String> alertas) {

    public record Proveedor(Long id, String razonSocial) {
    }

    /** Debe invocarse dentro de la transacción. */
    public static EntregaDTO from(OrdenCompra orden, LocalDate hoy, long recepciones, Instant ultimaRecepcion,
                                  long incidencias) {
        SituacionEntrega situacion = SituacionEntrega.de(orden, hoy);
        int pedidas = orden.getDetalles().stream().mapToInt(d -> d.getCantidad()).sum();
        int recibidas = orden.getDetalles().stream().mapToInt(d -> d.getCantidadRecibida()).sum();
        int retraso = situacion == SituacionEntrega.RETRASADA
                ? (int) ChronoUnit.DAYS.between(orden.getProgramacionEntrega().getFechaEstimada(), hoy)
                : 0;
        List<String> alertas = new ArrayList<>();
        boolean abierta = orden.getEstado().admiteRecepcion();
        if (situacion == SituacionEntrega.RETRASADA) {
            alertas.add("RETRASADA");
        }
        if (situacion == SituacionEntrega.SIN_PROGRAMAR) {
            alertas.add("SIN_PROGRAMAR");
        }
        if (abierta && incidencias > 0) {
            alertas.add("INCIDENCIAS");
        }
        return new EntregaDTO(orden.getId(), orden.getNumero(), orden.getEstado(), situacion,
                new Proveedor(orden.getProveedor().getId(), orden.getProveedor().getRazonSocial()), orden.getEmitidaEn(),
                ProgramacionEntregaDTO.from(orden.getProgramacionEntrega()), retraso, pedidas, recibidas,
                pedidas - recibidas, recepciones, ultimaRecepcion, incidencias, alertas);
    }
}
