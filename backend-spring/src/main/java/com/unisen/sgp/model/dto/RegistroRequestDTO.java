package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Alta pública de un trabajador (rol USUARIO) con <em>uno</em> de estos códigos:
 * <ul>
 *   <li>{@code codigoEmpresa}: el código permanente de su empresa. La cuenta queda PENDIENTE
 *       hasta que un gestor la apruebe.</li>
 *   <li>{@code codigoInvitacion}: un código de un solo uso generado por un gestor, que ya
 *       autoriza el alta: la cuenta queda ACTIVA (flujo anterior al Hito 2, se conserva).</li>
 * </ul>
 * El rol, el estado y la empresa nunca vienen del cliente: los fija el servidor.
 */
@RegistroRequestDTO.UnCodigoDeAlta
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

        @Schema(description = "Código de invitación de un solo uso (alternativa a codigoEmpresa).",
                example = "7KQ2-M9XA-4HPR-T3VW")
        @Size(max = 64, message = "El código de invitación no es válido.")
        String codigoInvitacion,

        @Schema(description = "Código permanente de la empresa (alternativa a codigoInvitacion). "
                + "La cuenta queda pendiente de aprobación.", example = "4HPR-T3VW-7KQ2-M9XA")
        @Size(max = 64, message = "El código de empresa no es válido.")
        String codigoEmpresa,

        @Schema(description = "Nombre para mostrar. Opcional: si se omite se usa el username.",
                example = "Ana Compras")
        @Size(max = Usuario.NOMBRE_MAX_LENGTH, message = "El nombre no puede superar 150 caracteres.")
        String nombre) {

    public RegistroRequestDTO {
        username = Usuario.normalizarUsername(username);
        email = Textos.minusculas(email);
        codigoInvitacion = Textos.vacioANull(codigoInvitacion);
        codigoEmpresa = Textos.vacioANull(codigoEmpresa);
        nombre = Textos.vacioANull(nombre);
    }

    /** Nombre a mostrar: el indicado o, si no hay, el username. */
    public String nombreParaMostrar() {
        return nombre != null ? nombre : username;
    }

    /** Ni la contraseña ni los códigos (credenciales) llegan a los logs. */
    @Override
    public String toString() {
        return "RegistroRequestDTO[username=" + username + ", email=" + email
                + ", password=***, codigoInvitacion=***, codigoEmpresa=***]";
    }

    /** Exige exactamente uno de los dos códigos; el error se asocia al campo, como el resto. */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = UnCodigoDeAltaValidator.class)
    public @interface UnCodigoDeAlta {
        String message() default "Indica un código de invitación o el código de tu empresa.";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class UnCodigoDeAltaValidator implements ConstraintValidator<UnCodigoDeAlta, RegistroRequestDTO> {

        @Override
        public boolean isValid(RegistroRequestDTO request, ConstraintValidatorContext context) {
            if (request == null) {
                return true;
            }
            boolean invitacion = request.codigoInvitacion() != null;
            boolean empresa = request.codigoEmpresa() != null;
            if (invitacion != empresa) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            if (invitacion) {
                error(context, "codigoEmpresa", "Usa el código de tu empresa o un código de invitación, no ambos.");
            } else {
                // Mismo mensaje que antes del Hito 2 para quien se registra con invitación.
                error(context, "codigoInvitacion", "El código de invitación es obligatorio.");
                error(context, "codigoEmpresa", "Indica el código de tu empresa o un código de invitación.");
            }
            return false;
        }

        private static void error(ConstraintValidatorContext context, String campo, String mensaje) {
            context.buildConstraintViolationWithTemplate(mensaje).addPropertyNode(campo).addConstraintViolation();
        }
    }
}
