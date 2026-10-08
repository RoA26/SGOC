package com.unisen.sgp.service;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.exception.InvitacionInvalidaException;
import com.unisen.sgp.exception.InvitacionInvalidaException.Motivo;
import com.unisen.sgp.model.dto.InvitacionResponseDTO;
import com.unisen.sgp.model.dto.LoginRequest;
import com.unisen.sgp.model.dto.LoginResponse;
import com.unisen.sgp.model.dto.RegistroRequestDTO;
import com.unisen.sgp.model.dto.UsuarioResponse;
import com.unisen.sgp.model.entity.CodigoInvitacion;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.EstadoUsuario;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.CodigoInvitacionRepository;
import com.unisen.sgp.repository.EmpresaRepository;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.security.CodigosInvitacion;
import com.unisen.sgp.security.JwtUtil;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.security.UsuarioPrincipal;
import com.unisen.sgp.tenant.TenantContextHolder;
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
    private static final String CAMPO_CODIGO_EMPRESA = "codigoEmpresa";

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final CodigoInvitacionRepository codigoInvitacionRepository;
    private final EmpresaRepository empresaRepository;
    private final Clock clock;

    public AuthService(AuthenticationManager authenticationManager, JwtUtil jwtUtil, UsuarioService usuarioService,
                       UsuarioRepository usuarioRepository, CodigoInvitacionRepository codigoInvitacionRepository,
                       EmpresaRepository empresaRepository, Clock clock) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.usuarioService = usuarioService;
        this.usuarioRepository = usuarioRepository;
        this.codigoInvitacionRepository = codigoInvitacionRepository;
        this.empresaRepository = empresaRepository;
        this.clock = clock;
    }

    /**
     * Valida las credenciales y emite un JWT.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException si el usuario
     *         no existe o la contraseña no coincide (indistinguibles a propósito)
     * @throws com.unisen.sgp.security.CuentaNoAutorizadaException si la cuenta está pendiente,
     *         rechazada, inactiva o su empresa desactivada (solo se informa tras validar la
     *         contraseña; → 403 sin token)
     */
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));

        UsuarioPrincipal principal = (UsuarioPrincipal) authentication.getPrincipal();
        String token = jwtUtil.generateToken(principal);
        return LoginResponse.bearer(token, jwtUtil.getExpiration(), UsuarioResponse.from(principal));
    }

    /**
     * Genera un código de invitación de un solo uso para la empresa actual: quien lo canjee
     * será USUARIO de esa empresa. Solo para gestores (GERENTE de su empresa o SUPER_ADMIN con
     * {@code X-Tenant-ID}); la comprobación vive aquí para que ninguna vía de entrada la salte.
     *
     * @param creadorId    gestor que genera el código (queda registrado)
     * @param horasValidez horas hasta la caducidad; {@code null} = {@value #HORAS_VALIDEZ_POR_DEFECTO}
     * @return el código en claro y su fecha de caducidad
     */
    @PreAuthorize(Permisos.GESTION)
    @Transactional
    public InvitacionResponseDTO generarCodigoInvitacion(Long creadorId, Integer horasValidez) {
        // La empresa del código la asigna Hibernate (@TenantId) desde el contexto.
        Long empresaId = TenantContextHolder.requerirEmpresa();
        int horas = horasValidez != null ? horasValidez : HORAS_VALIDEZ_POR_DEFECTO;
        Instant ahora = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiracion = ahora.plus(Duration.ofHours(horas));

        Usuario creador = usuarioRepository.getReferenceById(creadorId);
        CodigoInvitacion invitacion = codigoInvitacionRepository.save(new CodigoInvitacion(
                CodigosInvitacion.generarUnico(codigoInvitacionRepository::existsByCodigo), expiracion, creador, ahora));

        log.info("Invitación {} de la empresa {} generada por el usuario {} (caduca {}).", invitacion.getId(),
                empresaId, creadorId, expiracion);
        return new InvitacionResponseDTO(invitacion.getCodigo(), invitacion.getFechaExpiracion());
    }

    /**
     * Alta pública de un trabajador (rol USUARIO). Con {@code codigoEmpresa} queda PENDIENTE
     * hasta que un gestor lo apruebe; con {@code codigoInvitacion}, ACTIVO (el gestor ya lo
     * autorizó al generar la invitación). Rol, estado y empresa los fija el servidor.
     *
     * <p>Todo ocurre en una transacción: si falla cualquier paso (username o correo repetidos,
     * contraseña inválida...), no se crea el usuario y el código sigue disponible. El código se
     * valida <em>antes</em> que el username y el correo: sin un código válido no se puede
     * averiguar qué usuarios existen.
     *
     * @throws InvitacionInvalidaException si el código de invitación no existe, ya se usó o ha caducado
     * @throws CampoInvalidoException si el código de empresa no existe o su empresa está desactivada
     * @throws com.unisen.sgp.exception.DatoDuplicadoException si el username o el correo ya existen
     */
    @Transactional
    public UsuarioResponse registrar(RegistroRequestDTO request) {
        if (request.codigoEmpresa() != null) {
            return registrarConCodigoDeEmpresa(request);
        }
        return registrarConInvitacion(request);
    }

    /** Registro → empresa del código → TRABAJADOR (USUARIO) → PENDIENTE: sin acceso hasta que lo aprueben. */
    private UsuarioResponse registrarConCodigoDeEmpresa(RegistroRequestDTO request) {
        Empresa empresa = empresaRepository.findByCodigoEmpresa(CodigosInvitacion.normalizar(request.codigoEmpresa()))
                .orElseThrow(() -> new CampoInvalidoException(CAMPO_CODIGO_EMPRESA, "El código de empresa no es válido."));
        if (!empresa.isActiva()) {
            throw new CampoInvalidoException(CAMPO_CODIGO_EMPRESA, "La empresa de este código no está activa.");
        }

        Usuario usuario = usuarioService.crearUsuario(request.username(), request.email(),
                request.nombreParaMostrar(), request.password(), Rol.USUARIO, empresa, EstadoUsuario.PENDIENTE);

        log.info("Usuario {} registrado en la empresa {} con su código: pendiente de aprobación.", usuario.getId(),
                empresa.getId());
        return UsuarioResponse.from(usuario);
    }

    private UsuarioResponse registrarConInvitacion(RegistroRequestDTO request) {
        // 1. Buscar el código, bloqueando su fila frente a canjes simultáneos.
        CodigoInvitacion invitacion = codigoInvitacionRepository
                .findByCodigo(CodigosInvitacion.normalizar(request.codigoInvitacion()))
                .orElseThrow(() -> new InvitacionInvalidaException(Motivo.NO_EXISTE));

        // 2. Validar usado == false y fecha_expiracion > ahora.
        Instant ahora = clock.instant().truncatedTo(ChronoUnit.MICROS);
        invitacion.validarCanjeable(ahora);

        // El usuario entra en la empresa del código, que debe seguir activa.
        Empresa empresa = empresaRepository.findById(invitacion.getEmpresaId())
                .filter(Empresa::isActiva)
                .orElseThrow(() -> new InvitacionInvalidaException(Motivo.EMPRESA_INACTIVA));

        // 3 y 4. Crear el usuario con la contraseña hasheada en BCrypt.
        Usuario usuario = usuarioService.crearUsuario(request.username(), request.email(),
                request.nombreParaMostrar(), request.password(), Rol.USUARIO, empresa);

        // 5. Consumir el código: se guarda al confirmar esta misma transacción.
        invitacion.marcarUsado(usuario, ahora);

        log.info("Usuario {} registrado en la empresa {} con la invitación {}.", usuario.getId(), empresa.getId(),
                invitacion.getId());
        return UsuarioResponse.from(usuario);
    }

}
