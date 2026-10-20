package com.unisen.sgp.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Utilidades de datos compartidas por los tests de integración. */
public final class BaseDeDatosDePrueba {

    private BaseDeDatosDePrueba() {
    }

    /**
     * Vacía todas las tablas de negocio respetando las FK. SQL directo: con
     * {@code repository.deleteAll()} el borrado lógico dejaría las filas.
     */
    public static void vaciar(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM movimientos_inventario");
        jdbcTemplate.update("DELETE FROM detalles_devolucion_venta");
        jdbcTemplate.update("DELETE FROM devoluciones_venta");
        // La venta apunta a su autorización y la autorización a su venta: se rompe el ciclo.
        jdbcTemplate.update("UPDATE ventas SET autorizacion_id = NULL");
        jdbcTemplate.update("DELETE FROM autorizaciones_comerciales");
        jdbcTemplate.update("DELETE FROM pagos_venta");
        jdbcTemplate.update("DELETE FROM detalles_venta");
        jdbcTemplate.update("DELETE FROM ventas");
        jdbcTemplate.update("DELETE FROM cambios_precio");
        jdbcTemplate.update("DELETE FROM politicas_comerciales");
        jdbcTemplate.update("DELETE FROM incidencias_recepcion");
        jdbcTemplate.update("DELETE FROM detalles_recepcion");
        jdbcTemplate.update("DELETE FROM recepciones");
        jdbcTemplate.update("DELETE FROM existencias");
        jdbcTemplate.update("DELETE FROM documentos_orden_compra");
        // La orden apunta a su programación vigente y la programación a su orden: se rompe el ciclo.
        jdbcTemplate.update("UPDATE ordenes_compra SET programacion_entrega_id = NULL");
        jdbcTemplate.update("DELETE FROM programaciones_entrega");
        jdbcTemplate.update("DELETE FROM detalles_orden_compra");
        jdbcTemplate.update("DELETE FROM ordenes_compra");
        jdbcTemplate.update("DELETE FROM detalles_solicitud");
        jdbcTemplate.update("DELETE FROM solicitudes");
        jdbcTemplate.update("DELETE FROM productos");
        jdbcTemplate.update("DELETE FROM proveedores");
        jdbcTemplate.update("DELETE FROM codigos_invitacion");
        jdbcTemplate.update("DELETE FROM usuarios");
        jdbcTemplate.update("DELETE FROM empresas");
    }
}
