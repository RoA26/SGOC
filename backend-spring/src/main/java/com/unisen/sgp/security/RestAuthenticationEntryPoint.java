package com.unisen.sgp.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 401 en formato Problem Details cuando una ruta protegida se pide sin un JWT válido. */
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
        Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        String detail = reason != null ? reason.toString() : DEFAULT_DETAIL;

        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        responseWriter.write(request, response, HttpStatus.UNAUTHORIZED, "No autenticado", detail);
    }
}
