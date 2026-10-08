package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Aprovisionamiento de una empresa cliente (solo SUPER_ADMIN): la empresa y su GERENTE
 * fundador se crean juntos o no se crea nada. El código de empresa lo genera el servidor.
 */
public record EmpresaRequestDTO(
        @Schema(example = "Ferretería Andina S.A.S.")
        @NotBlank(message = "El nombre de la empresa es obligatorio.")
        @Size(max = 200, message = "El nombre no puede superar 200 caracteres.")
        String nombre,

        @Schema(example = "901234567-8")
        @NotBlank(message = "El NIT es obligatorio.")
        @Pattern(regexp = Patrones.NIT,
                message = "El NIT debe tener entre 5 y 15 dígitos y, opcionalmente, un dígito de verificación (p. ej. 900123456-7).")
        String nit,

        @NotNull(message = "Indica los datos del gerente fundador.")
        @Valid
        GerenteFundador gerente) {

    /** Normaliza antes de validar: el NIT pierde puntos y espacios ("901.234.567-8" es válido). */
    public EmpresaRequestDTO {
        nombre = Textos.limpiar(nombre);
        nit = nit == null ? null : Textos.mayusculas(nit.replaceAll("[\\s.]", ""));
    }

    /** Primer GERENTE de la empresa: queda ACTIVO, con la contraseña inicial indicada. */
    public record GerenteFundador(
            @Schema(example = "gerente.andina")
            @NotBlank(message = "El usuario es obligatorio.")
            @Pattern(regexp = Patrones.USERNAME,
                    message = "El usuario debe tener de 3 a 50 caracteres: letras, números, punto, guion o guion bajo, "
                            + "y empezar y terminar con letra o número.")
            String username,

            @Schema(example = "gerencia@andina.com")
            @NotBlank(message = "El correo es obligatorio.")
            @Email(regexp = Patrones.EMAIL, message = "El correo no tiene un formato válido.")
            @Size(max = Usuario.EMAIL_MAX_LENGTH, message = "El correo es demasiado largo.")
            String email,

            @Schema(example = "UnaClaveSegura2026")
            @NotBlank(message = "La contraseña es obligatoria.")
            @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
            String password,

            @Schema(description = "Nombre para mostrar. Opcional: si se omite se usa el username.",
                    example = "Laura Gerente")
            @Size(max = Usuario.NOMBRE_MAX_LENGTH, message = "El nombre no puede superar 150 caracteres.")
            String nombre) {

        public GerenteFundador {
            username = Usuario.normalizarUsername(username);
            email = Textos.minusculas(email);
            nombre = Textos.vacioANull(nombre);
        }

        public String nombreParaMostrar() {
            return nombre != null ? nombre : username;
        }

        /** La contraseña nunca llega a los logs. */
        @Override
        public String toString() {
            return "GerenteFundador[username=" + username + ", email=" + email + ", password=***]";
        }
    }
}
