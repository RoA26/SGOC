package com.unisen.sgp.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;

/**
 * Resultado de un login correcto.
 *
 * @param accessToken JWT a enviar como {@code Authorization: Bearer <token>}
 * @param tokenType   siempre {@code Bearer}
 * @param expiresIn   segundos de validez del token
 * @param usuario     datos del usuario autenticado (evita una llamada extra a /me)
 */
public record LoginResponse(
        String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "3600") long expiresIn,
        UsuarioResponse usuario) {

    public static final String BEARER = "Bearer";

    public static LoginResponse bearer(String accessToken, Duration validity, UsuarioResponse usuario) {
        return new LoginResponse(accessToken, BEARER, validity.toSeconds(), usuario);
    }
}
