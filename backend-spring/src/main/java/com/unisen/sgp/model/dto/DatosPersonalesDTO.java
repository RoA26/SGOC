package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nombres y apellidos de una persona: al darla de alta o al completar su perfil. */
public record DatosPersonalesDTO(
        @Schema(example = "Light")
        @NotBlank(message = "Los nombres son obligatorios.")
        @Size(max = Usuario.NOMBRES_MAX_LENGTH, message = "Los nombres no pueden superar 100 caracteres.")
        String nombres,

        @Schema(example = "Yagami")
        @NotBlank(message = "Los apellidos son obligatorios.")
        @Size(max = Usuario.APELLIDOS_MAX_LENGTH, message = "Los apellidos no pueden superar 100 caracteres.")
        String apellidos) {

    public DatosPersonalesDTO {
        nombres = Textos.limpiar(nombres);
        apellidos = Textos.limpiar(apellidos);
    }
}
