package com.unisen.sgp.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V14 (ADMIN_EMPRESA → ADMINISTRADOR_COMERCIAL), V15 (modelo comercial) y V16 (costo promedio
 * inicial) sobre una instalación en V13 con usuarios, productos y kárdex: nadie pierde su cuenta,
 * contraseña, empresa ni estado; los productos quedan sin precio de venta ni IVA presunto; el
 * costo promedio sale del kárdex existente y las restricciones nuevas protegen los datos.
 */
class V14aV16MigracionComercialTest {

    @Test
    void conservaLasCuentasCalculaElCostoPromedioYProtegeElModeloNuevo() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:migracion-v16;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        migrar(dataSource, "13");
        long base = jdbc.queryForObject("SELECT id FROM empresas WHERE nit = '000000000-0'", Long.class);
        String usuario = "INSERT INTO usuarios (username, email, password_hash, nombre, rol, empresa_id, estado, activo) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbc.update(usuario, "ana", "ana@base.com", "$2a$10$hashDeAna", "Ana", "ADMIN_EMPRESA", base, "ACTIVO", true);
        jdbc.update(usuario, "luis", "luis@base.com", "$2a$10$hashDeLuis", "Luis", "ADMIN_EMPRESA", base, "INACTIVO", false);
        jdbc.update(usuario, "tere", "tere@base.com", "$2a$10$hashDeTere", "Tere", "TRABAJADOR", base, "ACTIVO", true);
        long ana = id(jdbc, "SELECT id FROM usuarios WHERE username = 'ana'");
        jdbc.update("INSERT INTO proveedores (nit, razon_social, email, empresa_id) VALUES ('900111111-1', 'Aceros', "
                + "'a@a.com', ?)", base);
        long proveedor = id(jdbc, "SELECT id FROM proveedores");
        String producto = "INSERT INTO productos (sku, nombre, precio, proveedor_id, empresa_id) VALUES (?, ?, 1000, ?, ?)";
        jdbc.update(producto, "TOR-01", "Tornillo", proveedor, base);
        jdbc.update(producto, "SIN-01", "Sin costo", proveedor, base);
        jdbc.update(producto, "MIX-01", "Mezcla", proveedor, base);
        long tornillo = id(jdbc, "SELECT id FROM productos WHERE sku = 'TOR-01'");
        long sinCosto = id(jdbc, "SELECT id FROM productos WHERE sku = 'SIN-01'");
        long mezcla = id(jdbc, "SELECT id FROM productos WHERE sku = 'MIX-01'");
        String existencia = "INSERT INTO existencias (empresa_id, producto_id, cantidad) VALUES (?, ?, ?)";
        jdbc.update(existencia, base, tornillo, 12);
        jdbc.update(existencia, base, sinCosto, 4);
        jdbc.update(existencia, base, mezcla, 7);
        String ajuste = "INSERT INTO movimientos_inventario (empresa_id, producto_id, tipo, cantidad, saldo_resultante, "
                + "costo_unitario, motivo, clave_idempotencia, usuario_id, fecha) VALUES (?, ?, 'AJUSTE', ?, ?, ?, 'Ajuste', ?, ?, ?)";
        // Tornillo: 10 a 1.000, salen 3, entran 5 a 1.300 → (7 × 1.000 + 5 × 1.300) / 12 = 1.125.
        jdbc.update(ajuste, base, tornillo, 10, 10, new BigDecimal("1000"), "clave-0001", ana, ts("2026-10-01T10:00:00Z"));
        jdbc.update(ajuste, base, tornillo, -3, 7, null, "clave-0002", ana, ts("2026-10-02T10:00:00Z"));
        jdbc.update(ajuste, base, tornillo, 5, 12, new BigDecimal("1300"), "clave-0003", ana, ts("2026-10-03T10:00:00Z"));
        // Sin costo: inventario inicial sin costo → desconocido.
        jdbc.update(ajuste, base, sinCosto, 4, 4, null, "clave-0004", ana, ts("2026-10-01T10:00:00Z"));
        // Mezcla: 3 sin costo y luego 4 a 500 → no se mezcla lo desconocido: sigue desconocido.
        jdbc.update(ajuste, base, mezcla, 3, 3, null, "clave-0005", ana, ts("2026-10-01T10:00:00Z"));
        jdbc.update(ajuste, base, mezcla, 4, 7, new BigDecimal("500"), "clave-0006", ana, ts("2026-10-02T10:00:00Z"));

        migrar(dataSource, "16");

        // V14: mismo usuario, contraseña, empresa y estado; solo cambia el nombre del rol.
        assertThat(jdbc.queryForList("SELECT username || ':' || rol || ':' || estado || ':' || password_hash || ':' "
                + "|| CAST(empresa_id AS VARCHAR) FROM usuarios ORDER BY username", String.class))
                .containsExactly("ana:ADMINISTRADOR_COMERCIAL:ACTIVO:$2a$10$hashDeAna:" + base,
                        "luis:ADMINISTRADOR_COMERCIAL:INACTIVO:$2a$10$hashDeLuis:" + base,
                        "tere:TRABAJADOR:ACTIVO:$2a$10$hashDeTere:" + base);
        assertThatThrownBy(() -> jdbc.update("UPDATE usuarios SET rol = 'ADMIN_EMPRESA' WHERE username = 'ana'"))
                .as("el rol antiguo ya no es válido").isInstanceOf(DataIntegrityViolationException.class);

        // V15: sin precio de venta ni IVA presunto; la empresa no queda marcada como demo.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM productos WHERE precio_venta IS NULL AND tratamiento_iva IS NULL",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT demo FROM empresas WHERE id = ?", Boolean.class, base)).isFalse();
        assertThatThrownBy(() -> jdbc.update("UPDATE productos SET precio_venta = 100 WHERE id = ?", tornillo))
                .as("a la venta sin IVA clasificado").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE productos SET tratamiento_iva = 'GRAVADO', tarifa_iva = 0 WHERE id = ?",
                tornillo)).as("gravado a tarifa 0").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE productos SET tratamiento_iva = 'EXENTO', tarifa_iva = 19 WHERE id = ?",
                tornillo)).as("exento con tarifa").isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE productos SET precio_venta = 1500, tratamiento_iva = 'GRAVADO', tarifa_iva = 19 WHERE id = ?",
                tornillo);

        // V16: costo promedio desde el kárdex, sin tocar movimientos ni saldos.
        assertThat(jdbc.queryForObject("SELECT costo_promedio FROM existencias WHERE producto_id = ?", BigDecimal.class,
                tornillo)).isEqualByComparingTo("1125");
        assertThat(jdbc.queryForObject("SELECT costo_promedio FROM existencias WHERE producto_id = ?", BigDecimal.class,
                sinCosto)).isNull();
        assertThat(jdbc.queryForObject("SELECT costo_promedio FROM existencias WHERE producto_id = ?", BigDecimal.class,
                mezcla)).isNull();
        assertThat(jdbc.queryForObject("SELECT SUM(cantidad) FROM existencias", Integer.class)).isEqualTo(23);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimientos_inventario", Integer.class)).isEqualTo(6);

        // Restricciones del kárdex ampliado: una salida por venta necesita su línea.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO movimientos_inventario (empresa_id, producto_id, tipo, cantidad, "
                + "saldo_resultante, usuario_id, fecha) VALUES (?, ?, 'VENTA', -1, 11, ?, CURRENT_TIMESTAMP)", base, tornillo, ana))
                .as("venta sin línea").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO politicas_comerciales (empresa_id, descuento_max_trabajador, "
                + "descuento_max_administrador, venta_bajo_costo, actualizada_por_id) VALUES (?, 50, 10, 'PERMITIR', ?)", base, ana))
                .as("trabajador por encima del administrador").isInstanceOf(DataIntegrityViolationException.class);
    }

    private static java.sql.Timestamp ts(String instante) {
        return java.sql.Timestamp.from(java.time.Instant.parse(instante));
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
