package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.EstadoStock;

/**
 * Resultado de la búsqueda por escáner: el producto, sus existencias y por qué campo coincidió
 * (el código de barras tiene prioridad sobre el SKU).
 */
public record ProductoEncontradoDTO(
        ProductoResponseDTO producto,
        int existencias,
        EstadoStock estadoStock,
        Coincidencia coincidePor) {

    public enum Coincidencia {
        CODIGO_BARRAS,
        SKU
    }
}
