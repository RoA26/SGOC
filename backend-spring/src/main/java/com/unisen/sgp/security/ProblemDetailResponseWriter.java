package com.unisen.sgp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Escribe respuestas de error RFC 9457 desde la cadena de filtros, donde todavía no
 * existe el {@code @RestControllerAdvice}. Así 401/403 tienen el mismo formato que el resto.
 */
@Component
public class ProblemDetailResponseWriter {

    private final ObjectMapper objectMapper;

    public ProblemDetailResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                      String title, String detail) throws IOException {
        write(request, response, status, title, detail, Map.of());
    }

    /** @param properties propiedades adicionales del Problem Details (p. ej. {@code motivo}) */
    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                      String title, String detail, Map<String, Object> properties) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        properties.forEach(problem::setProperty);

        response.setStatus(status.value());
        // Igual que el resto de errores de la API; JSON es UTF-8 por definición (RFC 8259).
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
