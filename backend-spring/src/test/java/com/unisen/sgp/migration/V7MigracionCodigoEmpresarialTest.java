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
 * La V7 da a cada empresa existente un código empresarial único sin tocar nada más: ni su código
 * de registro de trabajadores ni sus usuarios.
 */
class V7MigracionCodigoEmpresarialTest {

    @Test
    void cadaEmpresaExistenteRecibeUnCodigoEstableYSeConservaSuCodigoDeRegistro() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:migracion-v7;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Instalación en V6: la Empresa Base (id 1), otra con un id alto y un gerente.
        migrarHasta(dataSource, "6");
        jdbc.update("INSERT INTO empresas (id, nombre, nit, codigo_empresa) VALUES (1234, 'Norte', '800000002-2', "
                + "'AAAA-BBBB-CCCC-DDDD')");
        long base = jdbc.queryForObject("SELECT id FROM empresas WHERE nit = '000000000-0'", Long.class);
        String registroBase = jdbc.queryForObject("SELECT codigo_empresa FROM empresas WHERE id = ?", String.class, base);
        jdbc.update("INSERT INTO usuarios (username, email, password_hash, nombre, rol, empresa_id, estado, activo) "
                + "VALUES ('gerente', 'g@x.com', 'h', 'G', 'GERENTE', ?, 'ACTIVO', TRUE)", base);

        migrarHasta(dataSource, "7");

        assertThat(jdbc.queryForList("SELECT codigo FROM empresas ORDER BY id", String.class))
                .containsExactly("EMP" + String.format("%03d", base), "EMP1234");
        // Nada más cambia.
        assertThat(jdbc.queryForObject("SELECT codigo_empresa FROM empresas WHERE id = ?", String.class, base))
                .isEqualTo(registroBase);
        assertThat(jdbc.queryForObject("SELECT codigo_empresa FROM empresas WHERE id = 1234", String.class))
                .isEqualTo("AAAA-BBBB-CCCC-DDDD");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE empresa_id = ? AND estado = 'ACTIVO'",
                Integer.class, base)).isEqualTo(1);

        // Restricciones nuevas: obligatorio, único y con formato.
        String alta = "INSERT INTO empresas (nombre, nit, codigo_empresa, codigo) VALUES ('X', ?, ?, ?)";
        jdbc.update(alta, "800000003-3", "AAAA-BBBB-CCCC-0001", "NASC01");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO empresas (nombre, nit, codigo_empresa) VALUES ('X', '800000004-4', 'AAAA-BBBB-CCCC-0002')"))
                .as("toda empresa tiene código").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(alta, "800000005-5", "AAAA-BBBB-CCCC-0003", "NASC01"))
                .as("código único").isInstanceOf(DataIntegrityViolationException.class);
        for (String invalido : List.of("nasc01", "1ABC", "AB", "NAS-01", "ABCDEFGHIJKL1")) {
            assertThatThrownBy(() -> jdbc.update(alta, "800000006-6", "AAAA-BBBB-CCCC-0004", invalido))
                    .as("formato de %s", invalido).isInstanceOf(DataIntegrityViolationException.class);
        }
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
