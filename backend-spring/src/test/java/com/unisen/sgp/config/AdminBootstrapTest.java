package com.unisen.sgp.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "app.bootstrap.admin.email=Admin@Unisen.com",
        "app.bootstrap.admin.password=AdminInicial123",
        "app.bootstrap.admin.nombre=Admin Unisen",
        // BD propia para no interferir con el resto de tests.
        "spring.datasource.url=jdbc:h2:mem:sgp-bootstrap;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class AdminBootstrapTest {

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AdminBootstrap adminBootstrap;

    @Test
    void creaElAdministradorAlArrancarYEsIdempotente() {
        Usuario admin = usuarioRepository.findByEmail("admin@unisen.com").orElseThrow();
        assertThat(admin.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(admin.getNombre()).isEqualTo("Admin Unisen");
        assertThat(admin.isActivo()).isTrue();
        assertThat(passwordEncoder.matches("AdminInicial123", admin.getPasswordHash())).isTrue();

        // Un segundo arranque no duplica ni modifica el usuario.
        adminBootstrap.run(new DefaultApplicationArguments());
        assertThat(usuarioRepository.count()).isEqualTo(1);
        assertThat(usuarioRepository.findByEmail("admin@unisen.com").orElseThrow().getPasswordHash())
                .isEqualTo(admin.getPasswordHash());
    }
}
