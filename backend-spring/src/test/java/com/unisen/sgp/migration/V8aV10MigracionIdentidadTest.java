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
 * V8 (roles ADMIN_EMPRESA y TRABAJADOR), V9 (nombres y apellidos) y V10 (revocación de
 * invitaciones) sobre una instalación en V7: nadie pierde su acceso, su empresa ni su nombre.
 */
class V8aV10MigracionIdentidadTest {

    @Test
    void traduceLosRolesConservaLosNombresYPreparaLaRevocacion() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:migracion-v10;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Instalación en V7: la Empresa Base, otra empresa y cuentas con los roles antiguos.
        migrarHasta(dataSource, "7");
        long base = jdbc.queryForObject("SELECT id FROM empresas WHERE nit = '000000000-0'", Long.class);
        jdbc.update("INSERT INTO empresas (nombre, nit, codigo_empresa, codigo) VALUES ('Norte', '800000002-2', "
                + "'AAAA-BBBB-CCCC-DDDD', 'NORT01')");
        long norte = jdbc.queryForObject("SELECT id FROM empresas WHERE codigo = 'NORT01'", Long.class);
        String usuario = "INSERT INTO usuarios (username, email, password_hash, nombre, rol, empresa_id, estado, activo) "
                + "VALUES (?, ?, 'hash', ?, ?, ?, ?, ?)";
        jdbc.update(usuario, "admin", "admin@unisen.com", "Administrador", "SUPER_ADMIN", null, "ACTIVO", true);
        jdbc.update(usuario, "laura", "laura@unisen.com", "Laura Gerente Ruiz", "GERENTE", base, "ACTIVO", true);
        jdbc.update(usuario, "ana", "ana@unisen.com", "María del Pilar De la Torre", "USUARIO", base, "ACTIVO", true);
        jdbc.update(usuario, "pedro", "pedro@unisen.com", "pedro", "USUARIO", base, "PENDIENTE", false);
        jdbc.update(usuario, "baja", "baja@norte.com", "Baja", "USUARIO", norte, "INACTIVO", false);
        long laura = idDe(jdbc, "laura");
        long ana = idDe(jdbc, "ana");
        long baja = idDe(jdbc, "baja");
        String invitacion = "INSERT INTO codigos_invitacion (codigo, fecha_expiracion, usado, usuario_creador_id, "
                + "usado_por_id, fecha_uso, empresa_id) VALUES (?, CURRENT_TIMESTAMP + INTERVAL '3' DAY, ?, ?, ?, ?, ?)";
        jdbc.update(invitacion, "USAD-AUSA-DAUS-ADA1", true, laura, ana, java.sql.Timestamp.valueOf("2026-10-01 10:00:00"),
                base);
        jdbc.update(invitacion, "VIGE-NTEV-IGEN-TE01", false, laura, null, null, base);

        migrarHasta(dataSource, "10");

        // V8: roles traducidos; estado, acceso y empresa intactos.
        assertThat(jdbc.queryForList("SELECT username || ':' || rol || ':' || estado || ':' || activo "
                + "FROM usuarios ORDER BY id", String.class)).containsExactly(
                "admin:SUPER_ADMIN:ACTIVO:TRUE", "laura:ADMIN_EMPRESA:ACTIVO:TRUE", "ana:TRABAJADOR:ACTIVO:TRUE",
                "pedro:TRABAJADOR:PENDIENTE:FALSE", "baja:TRABAJADOR:INACTIVO:FALSE");
        assertThat(jdbc.queryForObject("SELECT empresa_id FROM usuarios WHERE username = 'baja'", Long.class))
                .isEqualTo(norte);

        // V9: el nombre completo se conserva y no se parte; nombres y apellidos quedan por completar.
        assertThat(jdbc.queryForObject("SELECT nombre FROM usuarios WHERE username = 'ana'", String.class))
                .isEqualTo("María del Pilar De la Torre");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE nombres IS NOT NULL OR apellidos IS NOT NULL",
                Integer.class)).isZero();

        // V10: nada revocado; el historial de uso intacto.
        assertThat(jdbc.queryForList("SELECT codigo || ':' || usado || ':' || COALESCE(CAST(usado_por_id AS VARCHAR), '-') "
                + "|| ':' || COALESCE(CAST(fecha_revocacion AS VARCHAR), '-') FROM codigos_invitacion ORDER BY id",
                String.class)).containsExactly("USAD-AUSA-DAUS-ADA1:TRUE:" + ana + ":-", "VIGE-NTEV-IGEN-TE01:FALSE:-:-");

        // Restricciones nuevas.
        assertThatThrownBy(() -> jdbc.update(usuario, "x1", "x1@x.com", "X", "GERENTE", base, "ACTIVO", true))
                .as("rol antiguo").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(usuario, "x2", "x2@x.com", "X", "USUARIO", base, "ACTIVO", true))
                .as("rol antiguo").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE usuarios SET apellidos = 'Solo' WHERE username = 'pedro'"))
                .as("apellidos sin nombres").isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE usuarios SET nombres = ?, apellidos = ?, nombre = ? WHERE username = 'pedro'",
                "N".repeat(100), "A".repeat(100), "N".repeat(100) + " " + "A".repeat(100));

        assertThatThrownBy(() -> jdbc.update("UPDATE codigos_invitacion SET fecha_revocacion = CURRENT_TIMESTAMP, "
                + "revocado_por_id = ? WHERE codigo = 'USAD-AUSA-DAUS-ADA1'", laura))
                .as("una usada no se revoca").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE codigos_invitacion SET fecha_revocacion = CURRENT_TIMESTAMP "
                + "WHERE codigo = 'VIGE-NTEV-IGEN-TE01'"))
                .as("revocación sin autor").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE codigos_invitacion SET usado = TRUE, usado_por_id = ?, "
                + "fecha_uso = CURRENT_TIMESTAMP WHERE codigo = 'VIGE-NTEV-IGEN-TE01'", baja))
                .as("quien canjea es de la empresa de la invitación")
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE codigos_invitacion SET fecha_revocacion = CURRENT_TIMESTAMP, revocado_por_id = ? "
                + "WHERE codigo = 'VIGE-NTEV-IGEN-TE01'", laura);
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
