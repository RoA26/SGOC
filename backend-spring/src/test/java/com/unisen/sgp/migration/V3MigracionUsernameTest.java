package com.unisen.sgp.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * La migración V3 debe rellenar username en una BD que ya tiene usuarios (producción),
 * no solo en una vacía. Se migra hasta V2, se insertan usuarios y se aplica V3.
 */
class V3MigracionUsernameTest {

    @Test
    void rellenaUsernameDeLosUsuariosExistentes() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:migracion-v3;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        migrarHasta(dataSource, "2");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String insert = "INSERT INTO usuarios (email, password_hash, nombre, rol) VALUES (?, 'hash', 'X', 'USUARIO')";
        jdbc.update(insert, "Admin@Unisen.com");
        jdbc.update(insert, "ana+compras@empresa-a.com");
        jdbc.update(insert, "ana+compras@empresa-b.com");
        jdbc.update(insert, "un.nombre.de.usuario.extremadamente.largo.para.el.campo@x.com");

        migrarHasta(dataSource, "3");

        List<Map<String, Object>> filas = jdbc.queryForList("SELECT id, username FROM usuarios ORDER BY id");
        long idDuplicado = ((Number) filas.get(2).get("id")).longValue();
        assertThat(filas).extracting(fila -> fila.get("username")).containsExactly(
                "admin",
                "ana_compras",
                "ana_compras_" + idDuplicado,
                "un.nombre.de.usuario.extremadamente.larg");

        Integer nulos = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE username IS NULL", Integer.class);
        assertThat(nulos).isZero();
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
