package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Usuario;
import java.time.Instant;

/**
 * Empresa cliente. {@code codigoEmpresa} es el código permanente que el gerente comparte con
 * sus trabajadores para que se registren. {@code gerente} solo aparece al crearla.
 */
public record EmpresaResponseDTO(Long id, String nombre, String nit, boolean activa, String codigoEmpresa,
                                 Instant creadoEn, UsuarioResponse gerente) {

    public static EmpresaResponseDTO from(Empresa empresa) {
        return new EmpresaResponseDTO(empresa.getId(), empresa.getNombre(), empresa.getNit(), empresa.isActiva(),
                empresa.getCodigoEmpresa(), empresa.getCreadoEn(), null);
    }

    public static EmpresaResponseDTO from(Empresa empresa, Usuario gerenteFundador) {
        return new EmpresaResponseDTO(empresa.getId(), empresa.getNombre(), empresa.getNit(), empresa.isActiva(),
                empresa.getCodigoEmpresa(), empresa.getCreadoEn(), UsuarioResponse.from(gerenteFundador));
    }
}
