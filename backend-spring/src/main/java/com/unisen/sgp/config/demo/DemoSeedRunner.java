package com.unisen.sgp.config.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Ejecuta la seed de demostración al arrancar, SOLO con el perfil {@code demo}
 * ({@code SPRING_PROFILES_ACTIVE=demo}). Nunca en producción: si además hay un perfil de
 * producción activo, el arranque se detiene con un error en lugar de sembrar.
 *
 * <p>Con {@code app.demo.ejecutar-al-arrancar=false} el bean existe (p. ej. en los tests) pero no
 * siembra al arrancar.
 */
@Component
@Profile("demo")
public class DemoSeedRunner implements ApplicationRunner {

    static final Profiles PRODUCCION = Profiles.of("prod", "production", "produccion");

    private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);

    private final DemoSeedService seed;
    private final Environment environment;

    public DemoSeedRunner(DemoSeedService seed, Environment environment) {
        this.seed = seed;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        comprobarEntorno(environment);
        if (!environment.getProperty("app.demo.ejecutar-al-arrancar", Boolean.class, true)) {
            log.info("Perfil demo activo sin ejecución automática de la seed.");
            return;
        }
        log.warn("Perfil demo activo: sembrando las empresas de demostración (contraseña de DEMO_PASSWORD; "
                + "solo para desarrollo y pruebas).");
        seed.sembrar();
    }

    /** @throws IllegalStateException si el perfil demo convive con uno de producción */
    static void comprobarEntorno(Environment environment) {
        if (environment.acceptsProfiles(PRODUCCION)) {
            throw new IllegalStateException("La seed de demostración no puede ejecutarse con un perfil de producción "
                    + "activo. Quita el perfil 'demo'.");
        }
    }
}
