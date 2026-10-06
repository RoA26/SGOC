package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.Proveedor;
import java.time.Instant;

public record ProveedorResponseDTO(
        Long id,
        String nit,
        String razonSocial,
        String email,
        String telefono,
        String direccion,
        Instant creadoEn,
        Instant actualizadoEn) {

    public static ProveedorResponseDTO from(Proveedor proveedor) {
        return new ProveedorResponseDTO(
                proveedor.getId(),
                proveedor.getNit(),
                proveedor.getRazonSocial(),
                proveedor.getEmail(),
                proveedor.getTelefono(),
                proveedor.getDireccion(),
                proveedor.getCreadoEn(),
                proveedor.getActualizadoEn());
    }
}
