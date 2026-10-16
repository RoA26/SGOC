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
 *       administrador comparte para que se registren (nombre conservado por compatibilidad).</li>
 *   <li>{@code administrador}: administrador principal (el primer ADMIN_EMPRESA, creado con la
 *       empresa). Aparece al crearla y en el listado y la ficha del SUPER_ADMIN; se omite si la
 *       empresa aún no tiene ningún administrador.</li>
 * </ul>
 */
public record EmpresaResponseDTO(Long id, String nombre, String codigo, String nit, boolean activa,
                                 String codigoEmpresa, Instant creadoEn, UsuarioResponse administrador) {

    public static EmpresaResponseDTO from(Empresa empresa) {
        return from(empresa, null);
    }

    /** @param administradorPrincipal null si la empresa no tiene administrador */
    public static EmpresaResponseDTO from(Empresa empresa, Usuario administradorPrincipal) {
        return new EmpresaResponseDTO(empresa.getId(), empresa.getNombre(), empresa.getCodigo(), empresa.getNit(),
                empresa.isActiva(), empresa.getCodigoEmpresa(), empresa.getCreadoEn(),
                administradorPrincipal == null ? null : UsuarioResponse.from(administradorPrincipal));
    }
}
