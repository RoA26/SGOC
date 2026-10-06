package com.unisen.sgp.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI en {@code /swagger-ui.html}. Todas las operaciones exigen el esquema
 * {@code bearerAuth} salvo las marcadas como públicas (p. ej. el login).
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Unisen SGP API",
                version = "v1",
                description = "API REST del Sistema de Gestión de Órdenes de Compra"),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
@SecurityScheme(
        name = OpenApiConfig.BEARER_SCHEME,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Token obtenido en POST /api/auth/login")
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";
}
