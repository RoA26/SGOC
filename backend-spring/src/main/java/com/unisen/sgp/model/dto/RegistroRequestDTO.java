package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta de usuario mediante un código de invitación. */
public record RegistroRequestDTO(
        @Schema(example = "ana.compras")
        @NotBlank(message = "El usuario es obligatorio.")
        @Pattern(regexp = Patrones.USERNAME,
                message = "El usuario debe tener de 3 a 50 caracteres: letras, números, punto, guion o guion bajo, "
                        + "y empezar y terminar con letra o número.")
        String username,

        @Schema(example = "ana@unisen.com")
        @NotBlank(message = "El correo es obligatorio.")
        @Email(regexp = Patrones.EMAIL, message = "El correo no tiene un formato válido.")
        @Size(max = Usuario.EMAIL_MAX_LENGTH, message = "El correo es demasiado largo.")
        String email,

        @Schema(example = "UnaClaveSegura2026")
        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
        String password,

        @Schema(example = "7KQ2-M9XA-4HPR-T3VW")
        @NotBlank(message = "El código de invitación es obligatorio.")
        @Size(max = 64, message = "El código de invitación no es válido.")
        String codigoInvitacion,

        @Schema(description = "Nombre para mostrar. Opcional: si se omite se usa el username.",
                example = "Ana Compras")
        @Size(max = Usuario.NOMBRE_MAX_LENGTH, message = "El nombre no puede superar 150 caracteres.")
        String nombre) {

    public RegistroRequestDTO {
        username = Usuario.normalizarUsername(username);
        email = Textos.minusculas(email);
        codigoInvitacion = Textos.limpiar(codigoInvitacion);
        nombre = Textos.vacioANull(nombre);
    }

    /** Nombre a mostrar: el indicado o, si no hay, el username. */
    public String nombreParaMostrar() {
        return nombre != null ? nombre : username;
    }

    /** Ni la contraseña ni el código (credenciales) llegan a los logs. */
    @Override
    public String toString() {
        return "RegistroRequestDTO[username=" + username + ", email=" + email + ", password=***, codigoInvitacion=***]";
    }
}
