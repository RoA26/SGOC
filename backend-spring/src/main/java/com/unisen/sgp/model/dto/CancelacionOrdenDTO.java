package com.unisen.sgp.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelacionOrdenDTO(
        @NotBlank(message = "Indica el motivo de la cancelación.")
        @Size(max = 500, message = "El motivo no puede superar 500 caracteres.")
        String motivo) {

    public CancelacionOrdenDTO {
        motivo = Textos.vacioANull(motivo);
    }
}
