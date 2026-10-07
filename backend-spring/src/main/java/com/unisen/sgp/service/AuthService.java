package com.unisen.sgp.service;

import com.unisen.sgp.exception.InvitacionInvalidaException;
import com.unisen.sgp.exception.InvitacionInvalidaException.Motivo;
import com.unisen.sgp.model.dto.InvitacionResponseDTO;
import com.unisen.sgp.model.dto.LoginRequest;
import com.unisen.sgp.model.dto.LoginResponse;
import com.unisen.sgp.model.dto.RegistroRequestDTO;
import com.unisen.sgp.model.dto.UsuarioResponse;
import com.unisen.sgp.model.entity.CodigoInvitacion;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.CodigoInvitacionRepository;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.security.CodigosInvitacion;
import com.unisen.sgp.security.JwtUtil;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.security.UsuarioPrincipal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    public static final int HORAS_VALIDEZ_POR_DEFECTO = 72;
    /** Con 80 bits de entropía una colisión es prácticamente imposible; el límite evita un bucle infinito. */
    private static final int MAX_INTENTOS_GENERACION = 5;

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final CodigoInvitacionRepository codigoInvitacionRepository;
    private final Clock clock;

    public AuthService(AuthenticationManager authenticationManager, JwtUtil jwtUtil, UsuarioService usuarioService,
                       UsuarioRepository usuarioRepository, CodigoInvitacionRepository codigoInvitacionRepository,
                       Clock clock) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.usuarioService = usuarioService;
        this.usuarioRepository = usuarioRepository;
        this.codigoInvitacionRepository = codigoInvitacionRepository;
        this.clock = clock;
    }

    /**
     * Valida las credenciales y emite un JWT.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException si el usuario
     *         no existe o la contraseña no coincide (indistinguibles a propósito)
     * @throws org.springframework.security.authentication.DisabledException        si la cuenta
     *         está deshabilitada (solo se informa tras validar la contraseña)
     */
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));

        UsuarioPrincipal principal = (UsuarioPrincipal) authentication.getPrincipal();
        String token = jwtUtil.generateToken(principal);
        return LoginResponse.bearer(token, jwtUtil.getExpiration(), UsuarioResponse.from(principal));
    }

    /**
     * Genera un código de invitación de un solo uso. Solo para administradores: la
     * comprobación vive aquí (y no solo en el controlador) para que ninguna otra vía de
     * entrada pueda saltársela.
     *
     * @param creadorId    administrador que genera el código (queda registrado)
     * @param horasValidez horas hasta la caducidad; {@code null} = {@value #HORAS_VALIDEZ_POR_DEFECTO}
     * @return el código en claro y su fecha de caducidad
     */
    @PreAuthorize(Permisos.ADMIN)
    @Transactional
    public InvitacionResponseDTO generarCodigoInvitacion(Long creadorId, Integer horasValidez) {
        int horas = horasValidez != null ? horasValidez : HORAS_VALIDEZ_POR_DEFECTO;
        Instant ahora = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiracion = ahora.plus(Duration.ofHours(horas));

        Usuario creador = usuarioRepository.getReferenceById(creadorId);
        CodigoInvitacion invitacion = codigoInvitacionRepository.save(
                new CodigoInvitacion(generarCodigoUnico(), expiracion, creador, ahora));

        log.info("Invitación {} generada por el usuario {} (caduca {}).", invitacion.getId(), creadorId, expiracion);
        return new InvitacionResponseDTO(invitacion.getCodigo(), invitacion.getFechaExpiracion());
    }

    /**
     * Alta de un usuario canjeando un código de invitación. Todo ocurre en una transacción:
     * si falla cualquier paso (username o correo repetidos, contraseña inválida...), no se
     * crea el usuario y el código sigue disponible.
     *
     * <p>El código se valida <em>antes</em> que el username y el correo: sin una invitación
     * válida no se puede averiguar qué usuarios existen.
     *
     * @throws InvitacionInvalidaException si el código no existe, ya se usó o ha caducado
     * @throws com.unisen.sgp.exception.DatoDuplicadoException si el username o el correo ya existen
     */
    @Transactional
    public UsuarioResponse registrar(RegistroRequestDTO request) {
        // 1. Buscar el código, bloqueando su fila frente a canjes simultáneos.
        CodigoInvitacion invitacion = codigoInvitacionRepository
                .findByCodigo(CodigosInvitacion.normalizar(request.codigoInvitacion()))
                .orElseThrow(() -> new InvitacionInvalidaException(Motivo.NO_EXISTE));

        // 2. Validar usado == false y fecha_expiracion > ahora.
        Instant ahora = clock.instant().truncatedTo(ChronoUnit.MICROS);
        invitacion.validarCanjeable(ahora);

        // 3 y 4. Crear el usuario con la contraseña hasheada en BCrypt.
        Usuario usuario = usuarioService.crearUsuario(request.username(), request.email(),
                request.nombreParaMostrar(), request.password(), Rol.USUARIO);

        // 5. Consumir el código: se guarda al confirmar esta misma transacción.
        invitacion.marcarUsado(usuario, ahora);

        log.info("Usuario {} registrado con la invitación {}.", usuario.getId(), invitacion.getId());
        return UsuarioResponse.from(usuario);
    }

    private String generarCodigoUnico() {
        for (int intento = 0; intento < MAX_INTENTOS_GENERACION; intento++) {
            String codigo = CodigosInvitacion.generar();
            if (!codigoInvitacionRepository.existsByCodigo(codigo)) {
                return codigo;
            }
        }
        throw new IllegalStateException("No se pudo generar un código de invitación único.");
    }
}
