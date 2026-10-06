package com.unisen.sgp.config;

import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.service.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Crea el primer administrador al arrancar a partir de {@code ADMIN_EMAIL} y
 * {@code ADMIN_PASSWORD}. Es idempotente: si el correo ya existe no modifica nada,
 * de modo que puede quedarse configurado en el despliegue sin sobrescribir cambios.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final BootstrapProperties properties;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;

    public AdminBootstrap(BootstrapProperties properties, UsuarioRepository usuarioRepository,
                          UsuarioService usuarioService) {
        this.properties = properties;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
    }

    @Override
    public void run(ApplicationArguments args) {
        BootstrapProperties.Admin admin = properties.admin();
        if (admin == null || !admin.isConfigured()) {
            return;
        }
        String email = Usuario.normalizarEmail(admin.email());
        if (usuarioRepository.existsByEmail(email)) {
            log.info("Administrador inicial {} ya existe; no se realizan cambios.", email);
            return;
        }
        String nombre = StringUtils.hasText(admin.nombre()) ? admin.nombre() : "Administrador";
        usuarioService.crearUsuario(email, nombre, admin.password(), Rol.ADMIN);
        log.info("Administrador inicial {} creado.", email);
    }
}
