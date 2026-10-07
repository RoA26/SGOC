package com.unisen.sgp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Alta del administrador inicial ({@code app.bootstrap.admin.*}). Si el username, el correo
 * o la contraseña están vacíos, no se crea nada.
 */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(Admin admin) {

    public record Admin(String username, String email, String password, String nombre) {

        public boolean isConfigured() {
            return StringUtils.hasText(username) && StringUtils.hasText(email) && StringUtils.hasText(password);
        }

        @Override
        public String toString() {
            return "Admin[username=" + username + ", email=" + email + ", password=***, nombre=" + nombre + "]";
        }
    }
}
