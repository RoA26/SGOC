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

    /**
     * Username de quien hace la petición, para los registros de auditoría. Nunca falla: escribir
     * un log no debe poder interrumpir (ni deshacer) la operación que registra.
     */
    public static String paraAuditoria() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "(anónimo)" : authentication.getName();
    }
}
