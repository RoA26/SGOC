package com.unisen.sgp.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 403 en formato Problem Details cuando un usuario autenticado no tiene el rol requerido. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailResponseWriter responseWriter;

    public RestAccessDeniedHandler(ProblemDetailResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        responseWriter.write(request, response, HttpStatus.FORBIDDEN, "Acceso denegado",
                "No tienes permisos para acceder a este recurso.");
    }
}
