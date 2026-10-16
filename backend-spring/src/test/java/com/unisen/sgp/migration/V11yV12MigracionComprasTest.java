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
 * V11 (compras e inventario) y V12 (código de barras) sobre una instalación en V10 con datos:
 * nada se pierde, cada producto recibe su fila de existencias a 0 y las nuevas restricciones
 * impiden cruzar empresas en la propia BD.
 */
class V11yV12MigracionComprasTest {

    @Test
    void conservaLosDatosCreaLasExistenciasYAislaLasEmpresas() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:migracion-v12;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Instalación en V10: dos empresas con catálogo y una solicitud con dos líneas.
        migrarHasta(dataSource, "10");
        long base = jdbc.queryForObject("SELECT id FROM empresas WHERE nit = '000000000-0'", Long.class);
        jdbc.update("INSERT INTO empresas (nombre, nit, codigo_empresa, codigo) VALUES ('Norte', '800000002-2', "
                + "'AAAA-BBBB-CCCC-DDDD', 'NORT01')");
        long norte = jdbc.queryForObject("SELECT id FROM empresas WHERE codigo = 'NORT01'", Long.class);
        String usuario = "INSERT INTO usuarios (username, email, password_hash, nombre, rol, empresa_id, estado, activo) "
                + "VALUES (?, ?, 'hash', ?, ?, ?, 'ACTIVO', TRUE)";
        jdbc.update(usuario, "ana", "ana@base.com", "Ana", "TRABAJADOR", base);
        jdbc.update(usuario, "nora", "nora@norte.com", "Nora", "ADMIN_EMPRESA", norte);
        long ana = jdbc.queryForObject("SELECT id FROM usuarios WHERE username = 'ana'", Long.class);
        long nora = jdbc.queryForObject("SELECT id FROM usuarios WHERE username = 'nora'", Long.class);
        jdbc.update("INSERT INTO proveedores (nit, razon_social, email, empresa_id) VALUES ('900111111-1', 'Aceros', "
                + "'a@a.com', ?)", base);
        jdbc.update("INSERT INTO proveedores (nit, razon_social, email, empresa_id) VALUES ('900222222-2', 'Norteños', "
                + "'n@n.com', ?)", norte);
        long proveedorBase = jdbc.queryForObject("SELECT id FROM proveedores WHERE empresa_id = ?", Long.class, base);
        long proveedorNorte = jdbc.queryForObject("SELECT id FROM proveedores WHERE empresa_id = ?", Long.class, norte);
        String producto = "INSERT INTO productos (sku, nombre, precio, proveedor_id, empresa_id, activo) "
                + "VALUES (?, ?, 1000, ?, ?, ?)";
        jdbc.update(producto, "TOR-01", "Tornillo", proveedorBase, base, true);
        jdbc.update(producto, "TUE-01", "Tuerca", proveedorBase, base, true);
        jdbc.update(producto, "BAJA-01", "Dado de baja", proveedorBase, base, false);
        jdbc.update(producto, "NOR-01", "Producto Norte", proveedorNorte, norte, true);
        long tornillo = idProducto(jdbc, "TOR-01");
        long tuerca = idProducto(jdbc, "TUE-01");
        long productoNorte = idProducto(jdbc, "NOR-01");
        jdbc.update("INSERT INTO solicitudes (usuario_id, justificacion, empresa_id) VALUES (?, 'Reposición', ?)", ana, base);
        long solicitud = jdbc.queryForObject("SELECT id FROM solicitudes", Long.class);
        jdbc.update("INSERT INTO detalles_solicitud (solicitud_id, producto_id, cantidad) VALUES (?, ?, 5)", solicitud,
                tornillo);
        jdbc.update("INSERT INTO detalles_solicitud (solicitud_id, producto_id, cantidad) VALUES (?, ?, 3)", solicitud,
                tuerca);

        migrarHasta(dataSource, "12");

        // Datos intactos; las líneas de solicitud heredan la empresa de su solicitud.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM productos", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForList("SELECT producto_id || ':' || cantidad || ':' || empresa_id FROM detalles_solicitud "
                + "ORDER BY id", String.class)).containsExactly(tornillo + ":5:" + base, tuerca + ":3:" + base);
        // Stock mínimo 0 y sin código de barras para los productos existentes.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM productos WHERE stock_minimo = 0 AND codigo_barras IS NULL",
                Integer.class)).isEqualTo(4);
        // Una fila de existencias a 0 por producto (también los dados de baja), en su empresa.
        assertThat(jdbc.queryForList("SELECT p.sku || ':' || x.cantidad || ':' || (x.empresa_id = p.empresa_id) "
                + "FROM existencias x JOIN productos p ON p.id = x.producto_id ORDER BY p.sku", String.class))
                .containsExactly("BAJA-01:0:TRUE", "NOR-01:0:TRUE", "TOR-01:0:TRUE", "TUE-01:0:TRUE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimientos_inventario", Integer.class)).isZero();

        // La BD impide cruzar empresas: una línea de solicitud con un producto de otra empresa…
        assertThatThrownBy(() -> jdbc.update("INSERT INTO detalles_solicitud (solicitud_id, producto_id, cantidad, "
                + "empresa_id) VALUES (?, ?, 1, ?)", solicitud, productoNorte, base))
                .as("producto de otra empresa").isInstanceOf(DataIntegrityViolationException.class);
        // …una orden de la empresa base con el proveedor de Norte o creada por alguien de Norte.
        String orden = "INSERT INTO ordenes_compra (empresa_id, numero, proveedor_id, creado_por_id) VALUES (?, ?, ?, ?)";
        assertThatThrownBy(() -> jdbc.update(orden, base, 1, proveedorNorte, ana))
                .as("proveedor de otra empresa").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(orden, base, 1, proveedorBase, nora))
                .as("autor de otra empresa").isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(orden, base, 1, proveedorBase, ana);
        long ordenBase = jdbc.queryForObject("SELECT id FROM ordenes_compra", Long.class);
        assertThatThrownBy(() -> jdbc.update(orden, base, 1, proveedorBase, ana))
                .as("número repetido en la empresa").isInstanceOf(DataIntegrityViolationException.class);
        String linea = "INSERT INTO detalles_orden_compra (empresa_id, orden_id, producto_id, cantidad, costo_unitario, "
                + "cantidad_recibida) VALUES (?, ?, ?, ?, 1000, ?)";
        assertThatThrownBy(() -> jdbc.update(linea, base, ordenBase, productoNorte, 5, 0))
                .as("línea con producto de otra empresa").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(linea, base, ordenBase, tornillo, 5, 6))
                .as("recibir más de lo pedido").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE ordenes_compra SET estado = 'EMITIDA' WHERE id = ?", ordenBase))
                .as("emitida sin quién ni cuándo").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE existencias SET cantidad = -1 WHERE producto_id = ?", tornillo))
                .as("existencias negativas").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO existencias (empresa_id, producto_id) VALUES (?, ?)", base,
                tornillo)).as("dos filas de existencias del mismo producto")
                .isInstanceOf(DataIntegrityViolationException.class);

        // V12: código de barras texto (conserva ceros a la izquierda), único por empresa.
        jdbc.update("UPDATE productos SET codigo_barras = '0012345678905' WHERE id = ?", tornillo);
        assertThat(jdbc.queryForObject("SELECT codigo_barras FROM productos WHERE id = ?", String.class, tornillo))
                .isEqualTo("0012345678905");
        jdbc.update("UPDATE productos SET codigo_barras = '0012345678905' WHERE id = ?", productoNorte);
        assertThatThrownBy(() -> jdbc.update("UPDATE productos SET codigo_barras = '0012345678905' WHERE id = ?", tuerca))
                .as("código repetido en la empresa").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE productos SET codigo_barras = 'con espacio' WHERE id = ?", tuerca))
                .as("formato del código").isInstanceOf(DataIntegrityViolationException.class);
    }

    private static long idProducto(JdbcTemplate jdbc, String sku) {
        return jdbc.queryForObject("SELECT id FROM productos WHERE sku = ?", Long.class, sku);
    }

    private static void migrarHasta(JdbcDataSource dataSource, String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load()
                .migrate();
    }
}
