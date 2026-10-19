package com.unisen.sgp.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V13 (documentos, programación de entregas e incidencias) sobre una instalación en V12 con una
 * orden emitida y recibida en parte: nada cambia en los datos existentes y las restricciones
 * nuevas impiden cruzar empresas, apuntar a la programación de otra orden o reprogramar sin motivo.
 */
class V13MigracionEntregasTest {

    @Test
    void conservaLasOrdenesYProtegeLasTablasNuevas() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:migracion-v13;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        migrar(dataSource, "12");
        long base = jdbc.queryForObject("SELECT id FROM empresas WHERE nit = '000000000-0'", Long.class);
        jdbc.update("INSERT INTO empresas (nombre, nit, codigo_empresa, codigo) VALUES ('Norte', '800000002-2', "
                + "'AAAA-BBBB-CCCC-DDDD', 'NORT01')");
        long norte = jdbc.queryForObject("SELECT id FROM empresas WHERE codigo = 'NORT01'", Long.class);
        String usuario = "INSERT INTO usuarios (username, email, password_hash, nombre, rol, empresa_id, estado, activo) "
                + "VALUES (?, ?, 'hash', ?, ?, ?, 'ACTIVO', TRUE)";
        jdbc.update(usuario, "ana", "ana@base.com", "Ana", "ADMIN_EMPRESA", base);
        jdbc.update(usuario, "nora", "nora@norte.com", "Nora", "ADMIN_EMPRESA", norte);
        long ana = id(jdbc, "SELECT id FROM usuarios WHERE username = 'ana'");
        long nora = id(jdbc, "SELECT id FROM usuarios WHERE username = 'nora'");
        jdbc.update("INSERT INTO proveedores (nit, razon_social, email, empresa_id) VALUES ('900111111-1', 'Aceros', "
                + "'a@a.com', ?)", base);
        long proveedor = id(jdbc, "SELECT id FROM proveedores");
        jdbc.update("INSERT INTO productos (sku, nombre, precio, proveedor_id, empresa_id) VALUES ('TOR-01', 'Tornillo', "
                + "1000, ?, ?)", proveedor, base);
        long producto = id(jdbc, "SELECT id FROM productos");
        String orden = "INSERT INTO ordenes_compra (empresa_id, numero, proveedor_id, creado_por_id, estado, emitida_por_id, "
                + "emitida_en) VALUES (?, ?, ?, ?, 'RECIBIDA_PARCIALMENTE', ?, CURRENT_TIMESTAMP)";
        jdbc.update(orden, base, 1, proveedor, ana, ana);
        jdbc.update(orden, base, 2, proveedor, ana, ana);
        long orden1 = id(jdbc, "SELECT id FROM ordenes_compra WHERE numero = 1");
        long orden2 = id(jdbc, "SELECT id FROM ordenes_compra WHERE numero = 2");
        jdbc.update("INSERT INTO detalles_orden_compra (empresa_id, orden_id, producto_id, cantidad, costo_unitario, "
                + "cantidad_recibida) VALUES (?, ?, ?, 10, 1000, 4)", base, orden1, producto);
        long lineaOrden = id(jdbc, "SELECT id FROM detalles_orden_compra");
        jdbc.update("INSERT INTO recepciones (empresa_id, orden_id, clave_idempotencia, recibida_por_id) "
                + "VALUES (?, ?, 'clave-0001', ?)", base, orden1, ana);
        long recepcion = id(jdbc, "SELECT id FROM recepciones");

        migrar(dataSource, "13");

        // Nada cambia: la orden sigue igual, sin programación ni documento (se reconstruye al descargarla).
        assertThat(jdbc.queryForObject("SELECT estado || ':' || COALESCE(CAST(programacion_entrega_id AS VARCHAR), '-') "
                + "FROM ordenes_compra WHERE id = ?", String.class, orden1)).isEqualTo("RECIBIDA_PARCIALMENTE:-");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM documentos_orden_compra", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT cantidad_recibida FROM detalles_orden_compra", Integer.class)).isEqualTo(4);

        String programacion = "INSERT INTO programaciones_entrega (empresa_id, orden_id, version, fecha_estimada, "
                + "hora_desde, hora_hasta, motivo_cambio, registrada_por_id, registrada_en) "
                + "VALUES (?, ?, ?, DATE '2026-11-03', ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        jdbc.update(programacion, base, orden1, 1, null, null, null, ana);
        long p1 = id(jdbc, "SELECT id FROM programaciones_entrega WHERE orden_id = " + orden1);
        jdbc.update("UPDATE ordenes_compra SET programacion_entrega_id = ? WHERE id = ?", p1, orden1);

        assertThatThrownBy(() -> jdbc.update(programacion, base, orden1, 2, null, null, null, ana))
                .as("reprogramar sin motivo").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(programacion, base, orden1, 1, null, null, "Repetida", ana))
                .as("versión repetida").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(programacion, base, orden1, 2, java.sql.Time.valueOf("10:00:00"), null,
                "Media franja", ana)).as("franja incompleta").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(programacion, base, orden1, 2, java.sql.Time.valueOf("10:00:00"),
                java.sql.Time.valueOf("09:00:00"), "Franja invertida", ana))
                .as("franja invertida").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(programacion, base, orden1, 2, null, null, "Otra empresa", nora))
                .as("registrada por alguien de otra empresa").isInstanceOf(DataIntegrityViolationException.class);
        // La orden 2 no puede apuntar a la programación de la orden 1.
        assertThatThrownBy(() -> jdbc.update("UPDATE ordenes_compra SET programacion_entrega_id = ? WHERE id = ?", p1,
                orden2)).as("programación de otra orden").isInstanceOf(DataIntegrityViolationException.class);

        String incidencia = "INSERT INTO incidencias_recepcion (empresa_id, recepcion_id, detalle_orden_id, tipo, cantidad, "
                + "descripcion) VALUES (?, ?, ?, ?, ?, ?)";
        jdbc.update(incidencia, base, recepcion, lineaOrden, "DANADO", 2, null);
        jdbc.update(incidencia, base, recepcion, null, "PRODUCTO_INCORRECTO", null, "Llegó otra referencia");
        assertThatThrownBy(() -> jdbc.update(incidencia, base, recepcion, null, "FALTANTE", 1, null))
                .as("faltante sin línea").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(incidencia, base, recepcion, null, "OTRO", null, null))
                .as("otro sin descripción").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(incidencia, norte, recepcion, null, "OTRO", null, "Cruce"))
                .as("incidencia en una recepción de otra empresa").isInstanceOf(DataIntegrityViolationException.class);

        String documento = "INSERT INTO documentos_orden_compra (empresa_id, orden_id, version, motivo, contenido, "
                + "huella_sha256, generado_por_id, generado_en) VALUES (?, ?, ?, 'EMISION', '{}', ?, ?, CURRENT_TIMESTAMP)";
        jdbc.update(documento, base, orden1, 1, "a".repeat(64), ana);
        assertThatThrownBy(() -> jdbc.update(documento, base, orden1, 1, "b".repeat(64), ana))
                .as("dos documentos con la misma versión").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(documento, base, orden2, 1, "c".repeat(64), nora))
                .as("generado por alguien de otra empresa").isInstanceOf(DataIntegrityViolationException.class);
    }

    private static long id(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private static void migrar(JdbcDataSource dataSource, String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load()
                .migrate();
    }
}
