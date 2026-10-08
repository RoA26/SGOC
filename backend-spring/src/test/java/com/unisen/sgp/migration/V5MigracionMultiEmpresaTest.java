package com.unisen.sgp.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** La V5 convierte los datos de una instalación anterior (una sola empresa) al modelo multi-empresa. */
class V5MigracionMultiEmpresaTest {

    @Test
    void asignaLosDatosExistentesALaEmpresaBaseYElAdminPasaASuperAdmin() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:migracion-v5;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Instalación en V4: un ADMIN, un GERENTE, un USUARIO y datos de negocio.
        migrarHasta(dataSource, "4");
        String usuario = "INSERT INTO usuarios (username, email, password_hash, nombre, rol) VALUES (?, ?, 'hash', ?, ?)";
        jdbc.update(usuario, "admin", "admin@unisen.com", "Admin", "ADMIN");
        jdbc.update(usuario, "gerente", "gerente@unisen.com", "Gerente", "GERENTE");
        jdbc.update(usuario, "ana", "ana@unisen.com", "Ana", "USUARIO");
        long adminId = idDe(jdbc, "admin");
        long anaId = idDe(jdbc, "ana");
        jdbc.update("INSERT INTO proveedores (nit, razon_social, email) VALUES ('900123456-7', 'Aceros', 'v@a.com')");
        long proveedorId = jdbc.queryForObject("SELECT id FROM proveedores", Long.class);
        jdbc.update("INSERT INTO productos (sku, nombre, precio, proveedor_id) VALUES ('TOR-001', 'Tornillo', 1250, ?)",
                proveedorId);
        long productoId = jdbc.queryForObject("SELECT id FROM productos", Long.class);
        jdbc.update("INSERT INTO solicitudes (usuario_id, justificacion) VALUES (?, 'Reposición de tornillería.')", anaId);
        long solicitudId = jdbc.queryForObject("SELECT id FROM solicitudes", Long.class);
        jdbc.update("INSERT INTO detalles_solicitud (solicitud_id, producto_id, cantidad) VALUES (?, ?, 3)",
                solicitudId, productoId);
        jdbc.update("""
                INSERT INTO codigos_invitacion (codigo, fecha_expiracion, usuario_creador_id)
                VALUES ('AAAA-BBBB-CCCC-DDDD', CURRENT_TIMESTAMP, ?)""", adminId);

        migrarHasta(dataSource, "5");

        List<Map<String, Object>> empresas = jdbc.queryForList("SELECT id, nombre, activa FROM empresas");
        assertThat(empresas).hasSize(1);
        assertThat(empresas.get(0).get("nombre")).isEqualTo("Empresa Base");
        long base = ((Number) empresas.get(0).get("id")).longValue();

        assertThat(jdbc.queryForList("SELECT username, rol, empresa_id FROM usuarios ORDER BY id"))
                .extracting(f -> f.get("username") + ":" + f.get("rol") + ":" + f.get("empresa_id"))
                .containsExactly("admin:SUPER_ADMIN:null", "gerente:GERENTE:" + base, "ana:USUARIO:" + base);
        for (String tabla : List.of("proveedores", "productos", "solicitudes", "codigos_invitacion")) {
            assertThat(jdbc.queryForList("SELECT DISTINCT empresa_id FROM " + tabla, Long.class))
                    .as(tabla).containsExactly(base);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM detalles_solicitud", Integer.class)).isEqualTo(1);

        // Las nuevas restricciones protegen el modelo.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO usuarios (username, email, password_hash, nombre, rol) VALUES ('x', 'x@x.com', 'h', 'X', 'USUARIO')"))
                .as("un USUARIO sin empresa").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE usuarios SET rol = 'ADMIN' WHERE username = 'gerente'"))
                .as("el rol ADMIN ya no existe").isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("INSERT INTO empresas (nombre, nit) VALUES ('Otra', '800000000-1')");
        long otra = jdbc.queryForObject("SELECT id FROM empresas WHERE nit = '800000000-1'", Long.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO productos (sku, nombre, precio, proveedor_id, empresa_id) VALUES ('X-1', 'X', 1, ?, ?)",
                proveedorId, otra))
                .as("un producto no puede usar el proveedor de otra empresa")
                .isInstanceOf(DataIntegrityViolationException.class);
        // El mismo NIT sí puede existir en otra empresa.
        jdbc.update("INSERT INTO proveedores (nit, razon_social, email, empresa_id) VALUES ('900123456-7', 'Aceros', 'v@a.com', ?)",
                otra);
    }

    private static long idDe(JdbcTemplate jdbc, String username) {
        return jdbc.queryForObject("SELECT id FROM usuarios WHERE username = ?", Long.class, username);
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
