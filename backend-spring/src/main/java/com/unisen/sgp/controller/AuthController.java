package com.unisen.sgp.controller;

import com.unisen.sgp.model.dto.DatosPersonalesDTO;
import com.unisen.sgp.model.dto.LoginRequest;
import com.unisen.sgp.model.dto.LoginResponse;
import com.unisen.sgp.model.dto.RegistroRequestDTO;
import com.unisen.sgp.model.dto.UsuarioResponse;
import com.unisen.sgp.security.LimitadorAcceso;
import com.unisen.sgp.security.UsuarioPrincipal;
import com.unisen.sgp.service.AuthService;
import com.unisen.sgp.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final UsuarioService usuarioService;
    private final LimitadorAcceso limitador;

    public AuthController(AuthService authService, UsuarioService usuarioService, LimitadorAcceso limitador) {
        this.authService = authService;
        this.usuarioService = usuarioService;
        this.limitador = limitador;
    }

    @PostMapping("/login")
    @SecurityRequirements // Público: anula el requisito global de bearerAuth en Swagger.
    @Operation(summary = "Iniciar sesión", description = "Intercambia usuario y contraseña por un JWT.")
    @ApiResponse(responseCode = "200", description = "Login correcto")
    @ApiResponse(responseCode = "400", description = "Petición mal formada o datos inválidos")
    @ApiResponse(responseCode = "401", description = "Usuario o contraseña incorrectos")
    @ApiResponse(responseCode = "403",
            description = "Contraseña correcta pero la cuenta no está autorizada (motivo: PENDIENTE, RECHAZADO, INACTIVO o EMPRESA_INACTIVA)")
    @ApiResponse(responseCode = "429", description = "Demasiados intentos fallidos con ese usuario (Retry-After); "
            + "igual exista o no la cuenta")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return limitador.login(request.username(), () -> authService.login(request));
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Registrarse como trabajador",
            description = "Crea una cuenta con rol TRABAJADOR (trabajador). Con codigoEmpresa queda PENDIENTE hasta que un "
                    + "gestor la apruebe; con un codigoInvitacion de un solo uso queda ACTIVA. Se envía uno de los dos.")
    @ApiResponse(responseCode = "201",
            description = "Usuario creado: estado ACTIVO (invitación, ya puede iniciar sesión) o PENDIENTE (código de empresa)")
    @ApiResponse(responseCode = "400", description = "Datos inválidos; código de invitación inexistente, usado o caducado "
            + "(errors.codigoInvitacion) o código de empresa inexistente o de una empresa desactivada (errors.codigoEmpresa)")
    @ApiResponse(responseCode = "409", description = "El username o el correo ya están en uso (errors.username / errors.email)")
    @ApiResponse(responseCode = "429", description = "Demasiados intentos con ese código de empresa o demasiados "
            + "registros en total (Retry-After)")
    public UsuarioResponse registro(@Valid @RequestBody RegistroRequestDTO request) {
        return limitador.registro(request.codigoEmpresa(), () -> authService.registrar(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Usuario autenticado",
            description = "Perfil del dueño del token, leído de la BD en cada petición: nombre, rol, estado y empresa "
                    + "(nombre comercial y código). La interfaz lo usa para refrescar la sesión sin volver a entrar.")
    @ApiResponse(responseCode = "200", description = "Perfil del usuario")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado")
    public UsuarioResponse me(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return UsuarioResponse.from(principal);
    }

    @PatchMapping("/me")
    @Operation(summary = "Completar o corregir mis nombres y apellidos",
            description = "Solo la propia cuenta (la del token). El nombre visible se recalcula.")
    @ApiResponse(responseCode = "200", description = "Perfil actualizado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (errors.nombres, errors.apellidos)")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado")
    public UsuarioResponse actualizarMisDatos(@AuthenticationPrincipal UsuarioPrincipal principal,
                                              @Valid @RequestBody DatosPersonalesDTO request) {
        return usuarioService.actualizarDatosPersonales(principal.getId(), request);
    }
}
