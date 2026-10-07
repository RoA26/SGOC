package com.unisen.sgp.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica cada petición que trae {@code Authorization: Bearer <jwt>}.
 *
 * <p>Si el token falta o no es válido, la petición continúa sin autenticar: las rutas
 * públicas siguen funcionando y, en las protegidas, {@link RestAuthenticationEntryPoint}
 * responde 401 usando el motivo que este filtro deja en {@link #AUTH_ERROR_ATTRIBUTE}.
 *
 * <p>No se registra como bean: lo instancia {@code SecurityConfig} para que Spring Boot
 * no lo añada además como filtro de servlet (se ejecutaría fuera de la cadena de seguridad).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".ERROR";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();
    private final WebAuthenticationDetailsSource authenticationDetailsSource = new WebAuthenticationDetailsSource();

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = resolveBearerToken(request);
        if (token != null && securityContextHolderStrategy.getContext().getAuthentication() == null) {
            authenticate(token, request);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            // El "sub" del token es el username (Hito 4); los tokens antiguos con correo ya no resuelven.
            String username = jwtUtil.extractUsername(token);
            // Se consulta la BD en cada petición: desactivar un usuario revoca sus tokens al instante.
            UserDetails user = userDetailsService.loadUserByUsername(username);
            if (!user.isEnabled() || !user.isAccountNonLocked()) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, "La cuenta de usuario está deshabilitada.");
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
            authentication.setDetails(authenticationDetailsSource.buildDetails(request));

            SecurityContext context = securityContextHolderStrategy.createEmptyContext();
            context.setAuthentication(authentication);
            securityContextHolderStrategy.setContext(context);
        } catch (ExpiredJwtException ex) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, "El token ha expirado.");
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT rechazado: {}", ex.getMessage());
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, "El token no es válido.");
        } catch (UsernameNotFoundException ex) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, "El usuario del token ya no existe.");
        }
    }

    private static String resolveBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).strip();
        return token.isEmpty() ? null : token;
    }
}
