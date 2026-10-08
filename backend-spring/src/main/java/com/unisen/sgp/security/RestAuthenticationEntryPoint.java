package com.unisen.sgp.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Ruta protegida sin autenticación válida, en formato Problem Details:
 * <ul>
 *   <li>401 si no hay identidad: sin token, o token inválido, caducado o de un usuario que ya no existe;</li>
 *   <li>403 si el token es válido pero la BD ya no autoriza al usuario (pendiente, rechazado,
 *       inactivo o empresa desactivada), con la propiedad {@code motivo}.</li>
 * </ul>
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String DEFAULT_DETAIL = "Se requiere autenticación.";

    private final ProblemDetailResponseWriter responseWriter;

    public RestAuthenticationEntryPoint(ProblemDetailResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        if (request.getAttribute(JwtAuthenticationFilter.DENEGACION_ATTRIBUTE)
                instanceof CuentaNoAutorizadaException.Motivo motivo) {
            responseWriter.write(request, response, HttpStatus.FORBIDDEN, CuentaNoAutorizadaException.TITULO,
                    motivo.mensaje(), Map.of(CuentaNoAutorizadaException.PROPIEDAD_MOTIVO, motivo.name()));
            return;
        }

        Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        String detail = reason != null ? reason.toString() : DEFAULT_DETAIL;

        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        responseWriter.write(request, response, HttpStatus.UNAUTHORIZED, "No autenticado", detail);
    }
}
