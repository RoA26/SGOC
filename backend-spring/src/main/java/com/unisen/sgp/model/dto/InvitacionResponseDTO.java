package com.unisen.sgp.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Código generado. Es la única vez que la API lo devuelve: hay que copiarlo y entregarlo. */
public record InvitacionResponseDTO(
        @Schema(example = "7KQ2-M9XA-4HPR-T3VW") String codigo,
        Instant fechaExpiracion) {
}
