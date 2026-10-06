package com.unisen.sgp.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos para crear o actualizar un proveedor. */
public record ProveedorRequestDTO(
        @Schema(example = "900123456-7")
        @NotBlank(message = "El NIT es obligatorio.")
        @Pattern(regexp = Patrones.NIT,
                message = "El NIT debe tener entre 5 y 15 dígitos y, opcionalmente, un dígito de verificación (p. ej. 900123456-7).")
        String nit,

        @Schema(example = "Suministros Industriales S.A.S.")
        @NotBlank(message = "La razón social es obligatoria.")
        @Size(max = 200, message = "La razón social no puede superar 200 caracteres.")
        String razonSocial,

        @Schema(example = "compras@proveedor.com")
        @NotBlank(message = "El correo es obligatorio.")
        @Email(regexp = Patrones.EMAIL, message = "El correo no tiene un formato válido.")
        @Size(max = 320, message = "El correo es demasiado largo.")
        String email,

        @Schema(example = "+57 601 555 1234")
        @Pattern(regexp = Patrones.TELEFONO,
                message = "El teléfono admite de 7 a 20 caracteres: dígitos, espacios, +, - y paréntesis.")
        String telefono,

        @Schema(example = "Calle 100 # 15-20, Bogotá")
        @Size(max = 300, message = "La dirección no puede superar 300 caracteres.")
        String direccion) {

    /** Normaliza antes de validar: el NIT pierde puntos y espacios ("900.123.456-7" es válido). */
    public ProveedorRequestDTO {
        nit = nit == null ? null : Textos.mayusculas(nit.replaceAll("[\\s.]", ""));
        razonSocial = Textos.limpiar(razonSocial);
        email = Textos.minusculas(email);
        telefono = Textos.vacioANull(telefono);
        direccion = Textos.vacioANull(direccion);
    }
}
