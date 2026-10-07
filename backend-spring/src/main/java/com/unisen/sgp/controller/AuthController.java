package com.unisen.sgp.controller;

import com.unisen.sgp.model.dto.InvitacionRequestDTO;
import com.unisen.sgp.model.dto.InvitacionResponseDTO;
import com.unisen.sgp.model.dto.LoginRequest;
import com.unisen.sgp.model.dto.LoginResponse;
import com.unisen.sgp.model.dto.RegistroRequestDTO;
import com.unisen.sgp.model.dto.UsuarioResponse;
import com.unisen.sgp.security.UsuarioPrincipal;
import com.unisen.sgp.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @SecurityRequirements // Público: anula el requisito global de bearerAuth en Swagger.
    @Operation(summary = "Iniciar sesión", description = "Intercambia usuario y contraseña por un JWT.")
    @ApiResponse(responseCode = "200", description = "Login correcto")
    @ApiResponse(responseCode = "400", description = "Petición mal formada o datos inválidos")
    @ApiResponse(responseCode = "401", description = "Usuario o contraseña incorrectos")
    @ApiResponse(responseCode = "403", description = "Cuenta deshabilitada")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Registrarse con una invitación",
            description = "Crea una cuenta con rol USUARIO canjeando un código de invitación de un solo uso.")
    @ApiResponse(responseCode = "201", description = "Usuario creado; ya puede iniciar sesión")
    @ApiResponse(responseCode = "400",
            description = "Datos inválidos o código de invitación inexistente, usado o caducado (errors.codigoInvitacion)")
    @ApiResponse(responseCode = "409", description = "El username o el correo ya están en uso (errors.username / errors.email)")
    public UsuarioResponse registro(@Valid @RequestBody RegistroRequestDTO request) {
        return authService.registrar(request);
    }

    @PostMapping("/invitaciones")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generar código de invitación (ADMIN)",
            description = "Devuelve un código de un solo uso. El cuerpo es opcional; por defecto caduca en 72 horas.")
    @ApiResponse(responseCode = "201", description = "Código generado")
    @ApiResponse(responseCode = "400", description = "horasValidez fuera de rango")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado")
    @ApiResponse(responseCode = "403", description = "El usuario no es administrador")
    public InvitacionResponseDTO generarInvitacion(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody(required = false) InvitacionRequestDTO request) {
        Integer horasValidez = request != null ? request.horasValidez() : null;
        return authService.generarCodigoInvitacion(principal.getId(), horasValidez);
    }

    @GetMapping("/me")
    @Operation(summary = "Usuario autenticado", description = "Devuelve el perfil del dueño del token.")
    @ApiResponse(responseCode = "200", description = "Perfil del usuario")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado")
    public UsuarioResponse me(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return UsuarioResponse.from(principal);
    }
}
