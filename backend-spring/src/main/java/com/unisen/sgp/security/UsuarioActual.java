package com.unisen.sgp.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Usuario autenticado de la petición en curso, leído del {@code SecurityContext}. */
public final class UsuarioActual {

    private UsuarioActual() {
    }

    /**
     * @throws AuthenticationCredentialsNotFoundException si no hay usuario autenticado (→ 401)
     */
    public static UsuarioPrincipal obtener() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UsuarioPrincipal principal) {
            return principal;
        }
        throw new AuthenticationCredentialsNotFoundException("Se requiere autenticación.");
    }
}
