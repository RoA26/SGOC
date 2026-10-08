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
        jdbcTemplate.update("DELETE FROM detalles_solicitud");
        jdbcTemplate.update("DELETE FROM solicitudes");
        jdbcTemplate.update("DELETE FROM productos");
        jdbcTemplate.update("DELETE FROM proveedores");
        jdbcTemplate.update("DELETE FROM codigos_invitacion");
        jdbcTemplate.update("DELETE FROM usuarios");
        jdbcTemplate.update("DELETE FROM empresas");
    }
}
