package com.unisen.sgp.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.unisen.sgp.model.entity.Producto;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

        @Schema(example = "07702004003508", description = "Opcional. Se guarda como texto: conserva los ceros a la izquierda.")
        @Pattern(regexp = Patrones.CODIGO_BARRAS,
                message = "El código de barras admite de 3 a 64 letras, números o los signos . _ / + - (sin espacios).")
        String codigoBarras,

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

        @Schema(example = "10", description = "Opcional (0 por defecto): con existencias iguales o inferiores, stock crítico.")
        @Min(value = 0, message = "El stock mínimo no puede ser negativo.")
        @Max(value = 999_999, message = "El stock mínimo no puede superar 999999.")
        Integer stockMinimo,

        @Schema(example = "1")
        @NotNull(message = "El proveedor es obligatorio.")
        @Positive(message = "El proveedor no es válido.")
        Long proveedorId) {

    public ProductoRequestDTO {
        sku = Textos.mayusculas(sku);
        // Sin cambiar mayúsculas: en Code 128 "abc" y "ABC" son códigos distintos.
        codigoBarras = Textos.vacioANull(codigoBarras);
        nombre = Textos.limpiar(nombre);
        descripcion = Textos.vacioANull(descripcion);
        stockMinimo = stockMinimo == null ? 0 : stockMinimo;
    }

    public Producto.Datos datos() {
        return new Producto.Datos(sku, codigoBarras, nombre, descripcion, precio, stockMinimo);
    }
}
