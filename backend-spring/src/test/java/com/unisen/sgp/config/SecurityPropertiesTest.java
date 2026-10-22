package com.unisen.sgp.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Formato de {@code app.security.cors.allowed-origins} ({@code CORS_ALLOWED_ORIGINS}): un origen mal
 * escrito en el {@code .env} del VPS debe impedir el arranque, no dejar el login roto en silencio.
 */
class SecurityPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(SecurityProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues("app.security.bcrypt-strength=4");

    @Test
    void aceptaLosOrigenesDeLaTransicion() {
        runner.withPropertyValues("app.security.cors.allowed-origins=https://n3xora.duckdns.org,https://rrtf.duckdns.org")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SecurityProperties.class).cors().allowedOrigins())
                            .containsExactly("https://n3xora.duckdns.org", "https://rrtf.duckdns.org");
                });
    }

    @Test
    void recortaLosEspaciosTrasLaComa() {
        runner.withPropertyValues("app.security.cors.allowed-origins=https://n3xora.duckdns.org, https://rrtf.duckdns.org")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SecurityProperties.class).cors().allowedOrigins())
                            .containsExactly("https://n3xora.duckdns.org", "https://rrtf.duckdns.org");
                });
    }

    @Test
    void aceptaElValorPorDefectoConVite() {
        runner.withPropertyValues("app.security.cors.allowed-origins=https://rrtf.duckdns.org,http://localhost:5173")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void rechazaUnDominioSinEsquema() {
        runner.withPropertyValues("app.security.cors.allowed-origins=https://rrtf.duckdns.org,n3xora.duckdns.org")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rechazaUnaBarraFinalOUnaRuta() {
        runner.withPropertyValues("app.security.cors.allowed-origins=https://n3xora.duckdns.org/")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.security.cors.allowed-origins=https://n3xora.duckdns.org/api")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rechazaUnComodin() {
        runner.withPropertyValues("app.security.cors.allowed-origins=*")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rechazaUnaListaVacia() {
        runner.withPropertyValues("app.security.cors.allowed-origins=")
                .run(context -> assertThat(context).hasFailed());
    }
}
