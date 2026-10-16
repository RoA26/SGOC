package com.unisen.sgp.controller;

import com.unisen.sgp.model.dto.InvitacionRequestDTO;
import com.unisen.sgp.model.dto.InvitacionResponseDTO;
import com.unisen.sgp.model.dto.InvitacionResumenDTO;
import com.unisen.sgp.security.UsuarioPrincipal;
import com.unisen.sgp.service.InvitacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invitaciones de trabajadores de la empresa en la que se trabaja (ADMIN_EMPRESA, o SUPER_ADMIN
 * con X-Tenant-ID). La autorización (rol y empresa) vive en {@link InvitacionService}.
 */
@RestController
@RequestMapping("/api/auth/invitaciones")
@Tag(name = "Invitaciones")
public class InvitacionController {

    private final InvitacionService invitacionService;

    public InvitacionController(InvitacionService invitacionService) {
        this.invitacionService = invitacionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generar código de invitación (ADMIN_EMPRESA, o SUPER_ADMIN con X-Tenant-ID)",
            description = "Devuelve un código de un solo uso para la empresa actual. El cuerpo es opcional; por defecto "
                    + "caduca en 72 horas. Es la única vez que se devuelve el código completo.")
    @ApiResponse(responseCode = "201", description = "Código generado")
    @ApiResponse(responseCode = "400", description = "horasValidez fuera de rango o sin empresa en el contexto")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado")
    @ApiResponse(responseCode = "403", description = "El usuario no administra la empresa")
    public InvitacionResponseDTO generar(@AuthenticationPrincipal UsuarioPrincipal principal,
                                         @Valid @RequestBody(required = false) InvitacionRequestDTO request) {
        Integer horasValidez = request != null ? request.horasValidez() : null;
        return invitacionService.generar(principal.getId(), horasValidez);
    }

    @GetMapping
    @Operation(summary = "Listar las invitaciones de la empresa",
            description = "Con su estado (VIGENTE, USADA, REVOCADA, CADUCADA), quién la creó, quién la usó o revocó. "
                    + "Nunca devuelve el código completo.")
    @ApiResponse(responseCode = "400", description = "Sin empresa en el contexto")
    @ApiResponse(responseCode = "403", description = "El usuario no administra la empresa")
    public Page<InvitacionResumenDTO> listar(@ParameterObject @PageableDefault(size = 20, sort = {"creadoEn", "id"},
            direction = Sort.Direction.DESC) Pageable pageable) {
        return invitacionService.listar(pageable);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revocar una invitación vigente",
            description = "Baja lógica: deja de aceptarse y se conserva en el historial con quién la revocó y cuándo.")
    @ApiResponse(responseCode = "200", description = "Invitación revocada")
    @ApiResponse(responseCode = "403", description = "El usuario no administra la empresa")
    @ApiResponse(responseCode = "404", description = "No existe en la empresa actual")
    @ApiResponse(responseCode = "409", description = "Ya se usó, ya estaba revocada o ya caducó")
    public InvitacionResumenDTO revocar(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id) {
        return invitacionService.revocar(id, principal.getId());
    }
}
