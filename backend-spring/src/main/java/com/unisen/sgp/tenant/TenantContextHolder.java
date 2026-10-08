package com.unisen.sgp.tenant;

import com.unisen.sgp.exception.EmpresaNoSeleccionadaException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Empresa (tenant) de la petición en curso.
 *
 * <p>Vive en un {@link ThreadLocal}: lo fija {@link TenantFilter} al principio de cada
 * petición y lo borra al terminar, también si hay una excepción. Los hilos del servidor se
 * reutilizan, así que un valor olvidado se filtraría a la petición siguiente.
 *
 * <p>Vacío significa "sin empresa": peticiones públicas (login, registro) y el SUPER_ADMIN
 * en modo global. En ese caso Hibernate trabaja con el tenant raíz, sin filtrar
 * ({@link TenantIdentifierResolver}).
 */
public final class TenantContextHolder {

    private static final ThreadLocal<Long> EMPRESA_ACTUAL = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void establecer(Long empresaId) {
        EMPRESA_ACTUAL.set(Objects.requireNonNull(empresaId, "empresaId"));
    }

    public static Optional<Long> obtener() {
        return Optional.ofNullable(EMPRESA_ACTUAL.get());
    }

    /**
     * Empresa actual, obligatoria para escribir datos de negocio.
     *
     * @throws EmpresaNoSeleccionadaException si no hay ninguna (→ 400)
     */
    public static Long requerirEmpresa() {
        return obtener().orElseThrow(EmpresaNoSeleccionadaException::new);
    }

    public static void limpiar() {
        EMPRESA_ACTUAL.remove();
    }

    /**
     * Ejecuta {@code accion} dentro de una empresa y restaura después el contexto anterior.
     * Para procesos internos (tareas programadas, datos de prueba) fuera de una petición HTTP.
     */
    public static <T> T conEmpresa(Long empresaId, Supplier<T> accion) {
        Long anterior = EMPRESA_ACTUAL.get();
        establecer(empresaId);
        try {
            return accion.get();
        } finally {
            if (anterior == null) {
                limpiar();
            } else {
                EMPRESA_ACTUAL.set(anterior);
            }
        }
    }
}
