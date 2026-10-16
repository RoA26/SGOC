package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Usuario;
import java.time.Instant;

/**
 * Empresa cliente.
 *
 * <ul>
 *   <li>{@code codigo}: código empresarial, el identificador público y estable (p. ej. NASC01).</li>
 *   <li>{@code codigoEmpresa}: código de registro de trabajadores, aleatorio y secreto, que el
 *       gerente comparte para que se registren (nombre conservado por compatibilidad).</li>
 *   <li>{@code gerente}: administrador principal (el gerente fundador). Aparece al crearla y en el
 *       listado y la ficha del SUPER_ADMIN; se omite si la empresa aún no tiene ningún gerente.</li>
 * </ul>
 */
public record EmpresaResponseDTO(Long id, String nombre, String codigo, String nit, boolean activa,
                                 String codigoEmpresa, Instant creadoEn, UsuarioResponse gerente) {

    public static EmpresaResponseDTO from(Empresa empresa) {
        return from(empresa, null);
    }

    /** @param administradorPrincipal null si la empresa no tiene gerente */
    public static EmpresaResponseDTO from(Empresa empresa, Usuario administradorPrincipal) {
        return new EmpresaResponseDTO(empresa.getId(), empresa.getNombre(), empresa.getCodigo(), empresa.getNit(),
                empresa.isActiva(), empresa.getCodigoEmpresa(), empresa.getCreadoEn(),
                administradorPrincipal == null ? null : UsuarioResponse.from(administradorPrincipal));
    }
}
