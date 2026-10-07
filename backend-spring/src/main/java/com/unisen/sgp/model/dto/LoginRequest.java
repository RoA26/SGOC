package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credenciales de acceso (Hito 4: se inicia sesión con el nombre de usuario, no con el correo).
 *
 * <p>El username no se valida contra {@link Patrones#USERNAME}: los usuarios migrados desde
 * el correo pueden tener caracteres que hoy no se admitirían en un registro nuevo.
 */
public record LoginRequest(
        @Schema(example = "admin")
        @NotBlank(message = "El usuario es obligatorio.")
        @Size(max = Usuario.USERNAME_MAX_LENGTH, message = "El usuario es demasiado largo.")
        String username,

        @Schema(example = "CambiaEstaClave123")
        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(max = 128, message = "La contraseña es demasiado larga.")
        String password) {

    /** Mismo criterio que al almacenar: sin espacios y en minúsculas. */
    public LoginRequest {
        username = Usuario.normalizarUsername(username);
    }

    /** El {@code toString} generado por el record expondría la contraseña en logs. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
