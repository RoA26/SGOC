package com.unisen.sgp.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * La V6 añade el estado de cada usuario y el código permanente de empresa sin que nadie pierda
 * el acceso que tenía ni ninguna empresa quede sin código.
 */
class V6MigracionIdentidadTest {

    private static final String FORMATO_CODIGO = "^[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){3}$";

    @Test
    void conservaElAccesoDeCadaUsuarioYDaUnCodigoUnicoACadaEmpresa() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:migracion-v6;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Instalación en V5: la Empresa Base (sin código) y otra más; usuarios activos e inactivos.
        migrarHasta(dataSource, "5");
        jdbc.update("INSERT INTO empresas (nombre, nit) VALUES ('Norte', '800000002-2')");
        long base = jdbc.queryForObject("SELECT id FROM empresas WHERE nit = '000000000-0'", Long.class);
        String usuario = "INSERT INTO usuarios (username, email, password_hash, nombre, rol, activo, empresa_id) "
                + "VALUES (?, ?, 'hash', ?, ?, ?, ?)";
        jdbc.update(usuario, "admin", "admin@unisen.com", "Admin", "SUPER_ADMIN", true, null);
        jdbc.update(usuario, "gerente", "gerente@unisen.com", "Gerente", "GERENTE", true, base);
        jdbc.update(usuario, "ana", "ana@unisen.com", "Ana", "USUARIO", true, base);
        jdbc.update(usuario, "baja", "baja@unisen.com", "Baja", "USUARIO", false, base);

        migrarHasta(dataSource, "6");

        // Activo → ACTIVO; inactivo → INACTIVO. Nadie queda PENDIENTE ni pierde su acceso.
        assertThat(jdbc.queryForList("SELECT username || ':' || estado || ':' || activo FROM usuarios ORDER BY id",
                String.class))
                .containsExactly("admin:ACTIVO:TRUE", "gerente:ACTIVO:TRUE", "ana:ACTIVO:TRUE", "baja:INACTIVO:FALSE");

        // Cada empresa tiene su código, con el formato de la aplicación y sin repetirse.
        List<String> codigos = jdbc.queryForList("SELECT codigo_empresa FROM empresas", String.class);
        assertThat(codigos).hasSize(2).doesNotContainNull().doesNotHaveDuplicates()
                .allMatch(codigo -> codigo.matches(FORMATO_CODIGO));

        // Restricciones nuevas.
        String alta = "INSERT INTO usuarios (username, email, password_hash, nombre, rol, empresa_id, estado, activo) "
                + "VALUES (?, ?, 'h', 'X', 'USUARIO', ?, ?, ?)";
        assertThatThrownBy(() -> jdbc.update(alta, "x1", "x1@x.com", base, "ACTIVO", false))
                .as("activo y estado no pueden divergir").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(alta, "x2", "x2@x.com", base, "PENDIENTE", true))
                .as("activo y estado no pueden divergir").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(alta, "x3", "x3@x.com", base, "BLOQUEADO", false))
                .as("estado desconocido").isInstanceOf(DataIntegrityViolationException.class);
        // Un alta que no indica nada queda sin acceso.
        jdbc.update("INSERT INTO usuarios (username, email, password_hash, nombre, rol, empresa_id) "
                + "VALUES ('sin.estado', 's@x.com', 'h', 'S', 'USUARIO', ?)", base);
        assertThat(jdbc.queryForObject("SELECT estado || ':' || activo FROM usuarios WHERE username = 'sin.estado'",
                String.class)).isEqualTo("PENDIENTE:FALSE");

        assertThatThrownBy(() -> jdbc.update("INSERT INTO empresas (nombre, nit) VALUES ('Sin código', '800000003-3')"))
                .as("toda empresa tiene código").isInstanceOf(DataIntegrityViolationException.class);
        String repetido = codigos.get(0);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO empresas (nombre, nit, codigo_empresa) VALUES ('Copia', '800000004-4', ?)", repetido))
                .as("código único").isInstanceOf(DataIntegrityViolationException.class);
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
