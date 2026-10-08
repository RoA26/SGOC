package com.unisen.sgp.tenant;

import com.unisen.sgp.repository.EmpresaRepository;
import com.unisen.sgp.security.ProblemDetailResponseWriter;
import com.unisen.sgp.security.UsuarioPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fija la empresa (tenant) de la petición. Va justo después de {@code JwtAuthenticationFilter},
 * cuando ya se sabe quién llama:
 *
 * <ul>
 *   <li>GERENTE y USUARIO: siempre su propia empresa, la del JWT (que el filtro JWT ya
 *       comprobó contra la base de datos). La cabecera {@value #HEADER_TENANT} se ignora: nadie
 *       puede cambiar de empresa enviándola.</li>
 *   <li>SUPER_ADMIN: la empresa de {@value #HEADER_TENANT} si la envía (soporte,
 *       impersonación); si no, ninguna (modo global).</li>
 *   <li>Peticiones sin autenticar: ninguna.</li>
 *   <li>Rutas públicas de autenticación (login y registro): ninguna, aunque lleven un token.
 *       Se comportan siempre igual que una petición anónima: un código de invitación de otra
 *       empresa no debe quedar oculto por el tenant del token que se adjunte.</li>
 * </ul>
 *
 * <p>El contexto se borra siempre al terminar. No es un bean: lo instancia
 * {@code SecurityConfig} para que Spring Boot no lo registre también como filtro de servlet
 * (se ejecutaría antes de la autenticación y {@link OncePerRequestFilter} saltaría la segunda vez).
 */
public class TenantFilter extends OncePerRequestFilter {

    public static final String HEADER_TENANT = "X-Tenant-ID";

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);

    private final EmpresaRepository empresaRepository;
    private final ProblemDetailResponseWriter responseWriter;
    /** Rutas públicas de autenticación, que nunca trabajan dentro de una empresa. */
    private final RequestMatcher rutasSinEmpresa;

    public TenantFilter(EmpresaRepository empresaRepository, ProblemDetailResponseWriter responseWriter,
                        RequestMatcher rutasSinEmpresa) {
        this.empresaRepository = empresaRepository;
        this.responseWriter = responseWriter;
        this.rutasSinEmpresa = rutasSinEmpresa;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            UsuarioPrincipal usuario = rutasSinEmpresa.matches(request) ? null : usuarioAutenticado();
            if (usuario != null && !fijarEmpresa(usuario, request, response)) {
                return;
            }
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.limpiar();
        }
    }

    /** @return false si ya se respondió con un error y la petición no debe seguir. */
    private boolean fijarEmpresa(UsuarioPrincipal usuario, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (usuario.getRol().perteneceAEmpresa()) {
            if (usuario.getEmpresaId() == null) {
                // No debería ocurrir (ck_usuarios_empresa): nunca se sigue sin tenant, que sería ver todo.
                responseWriter.write(request, response, HttpStatus.FORBIDDEN, "Sin empresa",
                        "El usuario no pertenece a ninguna empresa.");
                return false;
            }
            TenantContextHolder.establecer(usuario.getEmpresaId());
            return true;
        }

        String cabecera = request.getHeader(HEADER_TENANT);
        if (!StringUtils.hasText(cabecera)) {
            return true; // SUPER_ADMIN en modo global
        }
        Long empresaId = parsear(cabecera.strip());
        if (empresaId == null) {
            responseWriter.write(request, response, HttpStatus.BAD_REQUEST, "Empresa no válida",
                    "La cabecera " + HEADER_TENANT + " debe ser el id numérico de una empresa.");
            return false;
        }
        if (!empresaRepository.existsById(empresaId)) {
            responseWriter.write(request, response, HttpStatus.BAD_REQUEST, "Empresa no válida",
                    "No existe la empresa con id " + empresaId + " indicada en " + HEADER_TENANT + ".");
            return false;
        }
        log.info("Soporte: {} {} {} en la empresa {}", usuario.getUsername(), request.getMethod(),
                request.getRequestURI(), empresaId);
        TenantContextHolder.establecer(empresaId);
        return true;
    }

    private static UsuarioPrincipal usuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof UsuarioPrincipal usuario
                ? usuario
                : null;
    }

    private static Long parsear(String valor) {
        try {
            long id = Long.parseLong(valor);
            return id > 0 ? id : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
