package com.unisen.sgp.controller;

import com.unisen.sgp.model.dto.RecepcionResponseDTO;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.service.RecepcionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recepciones")
@Tag(name = "Recepciones")
public class RecepcionController {

    private final RecepcionService recepcionService;

    public RecepcionController(RecepcionService recepcionService) {
        this.recepcionService = recepcionService;
    }

    @GetMapping
    @PreAuthorize(Permisos.GESTION)
    @Operation(summary = "Listar recepciones (paginado)", description = "Por defecto, las más recientes primero.")
    public Page<RecepcionResponseDTO> listar(
            @ParameterObject @PageableDefault(size = 10, sort = {"recibidaEn", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return recepcionService.listar(pageable);
    }
}
