package com.unisen.sgp.exception;

import com.unisen.sgp.security.CuentaNoAutorizadaException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce excepciones a respuestas RFC 9457 ({@code application/problem+json}).
 *
 * <p>Al extender {@link ResponseEntityExceptionHandler} los errores estándar de Spring MVC
 * (JSON mal formado, método no soportado, 404...) ya salen como Problem Details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Restricción de BD → campo del formulario y mensaje para el usuario. */
    private record Restriccion(String nombre, String campo, String mensaje) {
    }

    private static final List<Restriccion> RESTRICCIONES = List.of(
            new Restriccion("uq_proveedores_nit", "nit",
                    "Ya existe un proveedor con este NIT (también se cuentan los dados de baja)."),
            new Restriccion("uq_productos_sku", "sku",
                    "Ya existe un producto con este SKU (también se cuentan los dados de baja)."),
            new Restriccion("uq_empresas_nit", "nit", "Ya existe una empresa con este NIT."),
            new Restriccion("uq_empresas_codigo_empresa", "codigoEmpresa",
                    "No se pudo asignar un código de empresa único. Inténtalo de nuevo."),
            new Restriccion("fk_productos_proveedor", "proveedorId",
                    "El proveedor seleccionado no existe."),
            new Restriccion("uq_detalles_solicitud_producto", "detalles",
                    "Un producto solo puede aparecer una vez en la solicitud."),
            new Restriccion("fk_detalles_solicitud_producto", "detalles",
                    "Uno de los productos seleccionados no existe."),
            new Restriccion("uq_usuarios_username", "username",
                    "Ese nombre de usuario ya está en uso."),
            new Restriccion("uq_usuarios_email", "email",
                    "Ya existe un usuario con este correo."));

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex) {
        ProblemDetail problem = problem(HttpStatus.UNAUTHORIZED, "Credenciales inválidas",
                "Usuario o contraseña incorrectos.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(problem);
    }

    /**
     * Cuenta sin acceso operativo (solo tras validar la contraseña): pendiente de aprobación,
     * rechazada, inactiva o de una empresa desactivada, con el {@code motivo}; o bloqueada o
     * expirada en general.
     */
    @ExceptionHandler(AccountStatusException.class)
    public ProblemDetail handleAccountStatus(AccountStatusException ex) {
        if (ex instanceof CuentaNoAutorizadaException denegada) {
            ProblemDetail problem = problem(HttpStatus.FORBIDDEN, CuentaNoAutorizadaException.TITULO,
                    denegada.getMotivo().mensaje());
            problem.setProperty(CuentaNoAutorizadaException.PROPIEDAD_MOTIVO, denegada.getMotivo().name());
            return problem;
        }
        return problem(HttpStatus.FORBIDDEN, "Cuenta no disponible", "La cuenta de usuario está deshabilitada.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "No autenticado", "No se pudo completar la autenticación.");
    }

    /** Denegaciones de seguridad a nivel de método (p. ej. {@code @PreAuthorize}). */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Acceso denegado", "No tienes permisos para acceder a este recurso.");
    }

    /** Username o correo repetidos: 409 con el campo afectado para marcarlo en el formulario. */
    @ExceptionHandler(DatoDuplicadoException.class)
    public ProblemDetail handleDatoDuplicado(DatoDuplicadoException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Conflicto de datos", ex.getMessage());
        problem.setProperty("errors", Map.of(ex.getCampo(), ex.getMessage()));
        return problem;
    }

    /** Otra transacción retiene el mismo registro (p. ej. dos canjes simultáneos del mismo código). */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ProblemDetail handlePessimisticLocking(PessimisticLockingFailureException ex) {
        log.warn("Conflicto de bloqueo: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, "Conflicto de concurrencia",
                "Otra operación está usando los mismos datos. Inténtalo de nuevo.");
    }

    /** Un SUPER_ADMIN en modo global intenta escribir datos de empresa sin elegir una. */
    @ExceptionHandler(EmpresaNoSeleccionadaException.class)
    public ProblemDetail handleEmpresaNoSeleccionada(EmpresaNoSeleccionadaException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Empresa no seleccionada", ex.getMessage());
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail handleNoEncontrado(RecursoNoEncontradoException ex) {
        return problem(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage());
    }

    @ExceptionHandler(ConflictoException.class)
    public ProblemDetail handleConflicto(ConflictoException ex) {
        return problem(HttpStatus.CONFLICT, "Operación no permitida", ex.getMessage());
    }

    /** Regla de negocio sobre un campo concreto: mismo formato que los errores de validación. */
    @ExceptionHandler(CampoInvalidoException.class)
    public ProblemDetail handleCampoInvalido(CampoInvalidoException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Datos inválidos", ex.getMessage());
        problem.setProperty("errors", Map.of(ex.getCampo(), ex.getMessage()));
        return problem;
    }

    /**
     * Violación de una restricción de la BD (UNIQUE, FK, CHECK). Es la red de seguridad ante
     * duplicados, incluso con peticiones concurrentes. Se identifica la restricción para
     * indicar el campo afectado; los detalles SQL nunca llegan al cliente.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        String causa = String.valueOf(NestedExceptionUtils.getMostSpecificCause(ex).getMessage())
                .toLowerCase(Locale.ROOT);
        Restriccion restriccion = RESTRICCIONES.stream()
                .filter(r -> causa.contains(r.nombre()))
                .findFirst()
                .orElse(null);

        if (restriccion == null) {
            log.warn("Violación de integridad no catalogada: {}", causa);
            return problem(HttpStatus.CONFLICT, "Conflicto de datos",
                    "La operación entra en conflicto con los datos existentes.");
        }
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Conflicto de datos", restriccion.mensaje());
        problem.setProperty("errors", Map.of(restriccion.campo(), restriccion.mensaje()));
        return problem;
    }

    /** Ordenación por un campo inexistente (?sort=foo). */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handlePropertyReference(PropertyReferenceException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Parámetro inválido",
                "No se puede ordenar por '" + ex.getPropertyName() + "': el campo no existe.");
    }

    /** Último recurso: registra el error y no filtra detalles internos al cliente. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Error no controlado", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Se produjo un error inesperado. Inténtalo de nuevo más tarde.");
    }

    /** Errores de Bean Validation: devuelve un mapa campo → mensaje en {@code errors}. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Datos inválidos",
                "La petición contiene datos inválidos.");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
