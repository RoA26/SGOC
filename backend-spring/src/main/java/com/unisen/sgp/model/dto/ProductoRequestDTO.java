package com.unisen.sgp.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Datos para crear o actualizar un producto. */
public record ProductoRequestDTO(
        @Schema(example = "TOR-M8-100")
        @NotBlank(message = "El SKU es obligatorio.")
        @Pattern(regexp = Patrones.SKU,
                message = "El SKU admite de 3 a 40 letras, números, puntos, guiones o guiones bajos.")
        String sku,

        @Schema(example = "Tornillo hexagonal M8 x 100")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres.")
        String nombre,

        @Size(max = 1000, message = "La descripción no puede superar 1000 caracteres.")
        String descripcion,

        @Schema(example = "1250.50")
        @NotNull(message = "El precio es obligatorio.")
        @Positive(message = "El precio debe ser mayor que 0.")
        @Digits(integer = 12, fraction = 2, message = "El precio admite hasta 12 enteros y 2 decimales.")
        BigDecimal precio,

        @Schema(example = "1")
        @NotNull(message = "El proveedor es obligatorio.")
        @Positive(message = "El proveedor no es válido.")
        Long proveedorId) {

    public ProductoRequestDTO {
        sku = Textos.mayusculas(sku);
        nombre = Textos.limpiar(nombre);
        descripcion = Textos.vacioANull(descripcion);
    }
}
