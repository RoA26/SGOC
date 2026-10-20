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
 * Crea el primer SUPER_ADMIN (operador de la plataforma, sin empresa) al arrancar a partir de {@code ADMIN_USERNAME},
 * {@code ADMIN_EMAIL} y {@code ADMIN_PASSWORD}. Es idempotente: si el username o el correo
 * ya existen no modifica nada, de modo que puede quedarse configurado en el despliegue sin
 * sobrescribir cambios.
 *
 * <p>Es la cuenta raíz: crea las empresas y su administrador principal (ADMINISTRADOR_COMERCIAL) desde
 * «Empresas» ({@code POST /api/v1/empresas}).
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
        String username = Usuario.normalizarUsername(admin.username());
        String email = Usuario.normalizarEmail(admin.email());
        if (usuarioRepository.existsByUsername(username) || usuarioRepository.existsByEmail(email)) {
            log.info("Administrador inicial {} ya existe; no se realizan cambios.", username);
            return;
        }
        String nombres = StringUtils.hasText(admin.nombre()) ? admin.nombre() : "Administrador";
        String apellidos = StringUtils.hasText(admin.apellidos()) ? admin.apellidos() : null;
        // Operador de la plataforma: no pertenece a ninguna empresa.
        usuarioService.crearUsuario(username, email, nombres, apellidos, admin.password(), Rol.SUPER_ADMIN, null);
        log.info("Super administrador inicial {} creado.", username);
    }
}
