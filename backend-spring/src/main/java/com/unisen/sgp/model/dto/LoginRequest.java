package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Credenciales de acceso. */
public record LoginRequest(
        @Schema(example = "admin@unisen.com")
        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo no tiene un formato válido.")
        @Size(max = Usuario.EMAIL_MAX_LENGTH, message = "El correo es demasiado largo.")
        String email,

        @Schema(example = "CambiaEstaClave123")
        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(max = 128, message = "La contraseña es demasiado larga.")
        String password) {

    /** Se eliminan espacios accidentales del correo antes de validarlo. */
    public LoginRequest {
        email = email == null ? null : email.strip();
    }

    /** El {@code toString} generado por el record expondría la contraseña en logs. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
