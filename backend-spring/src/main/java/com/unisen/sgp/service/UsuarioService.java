package com.unisen.sgp.service;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.exception.DatoDuplicadoException;
import com.unisen.sgp.exception.RecursoNoEncontradoException;
import com.unisen.sgp.model.dto.CambioEstadoUsuarioDTO;
import com.unisen.sgp.model.dto.TrabajadorResponseDTO;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.EstadoUsuario;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.security.UsuarioActual;
import com.unisen.sgp.security.UsuarioPrincipal;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    public static final int PASSWORD_MIN_LENGTH = 8;
    /** BCrypt solo procesa los primeros 72 bytes: más allá, dos contraseñas distintas colisionarían. */
    public static final int PASSWORD_MAX_BYTES = 72;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Alta ya autorizada (estado ACTIVO): administrador inicial, gerente fundador o canje de
     * una invitación que generó un gestor. Un autorregistro debe usar la variante con estado.
     *
     * @see #crearUsuario(String, String, String, String, Rol, Empresa, EstadoUsuario)
     */
    @Transactional
    public Usuario crearUsuario(String username, String email, String nombre, String password, Rol rol,
                                Empresa empresa) {
        return crearUsuario(username, email, nombre, password, rol, empresa, EstadoUsuario.ACTIVO);
    }

    /**
     * Da de alta un usuario con la contraseña hasheada con BCrypt.
     *
     * <p>Se une a la transacción del llamador: si algo falla después (p. ej. al marcar la
     * invitación), el alta se deshace.
     *
     * <p>Username y correo son únicos en toda la plataforma (no por empresa): identifican a
     * la persona en el login, antes de conocer su empresa.
     *
     * @param empresa empresa del usuario; null solo para SUPER_ADMIN
     * @param estado  PENDIENTE (autorregistro con el código de empresa) o ACTIVO
     * @throws DatoDuplicadoException si el username o el correo ya están en uso
     * @throws CampoInvalidoException si la contraseña no cumple la política
     */
    @Transactional
    public Usuario crearUsuario(String username, String email, String nombre, String password, Rol rol,
                                Empresa empresa, EstadoUsuario estado) {
        validarPassword(password);
        String usernameNormalizado = Usuario.normalizarUsername(username);
        String emailNormalizado = Usuario.normalizarEmail(email);
        if (usuarioRepository.existsByUsername(usernameNormalizado)) {
            throw DatoDuplicadoException.username();
        }
        if (usuarioRepository.existsByEmail(emailNormalizado)) {
            throw DatoDuplicadoException.email();
        }
        Usuario usuario = new Usuario(usernameNormalizado, emailNormalizado,
                passwordEncoder.encode(password), nombre, rol, empresa, estado);
        // flush: si una alta concurrente se adelanta, la violación UNIQUE salta aquí
        // (→ 409 en GlobalExceptionHandler) y no al confirmar, fuera del servicio.
        return usuarioRepository.saveAndFlush(usuario);
    }

    /**
     * Trabajadores (rol USUARIO) de la empresa de la petición, opcionalmente por estado (p. ej.
     * los PENDIENTES de aprobar). GERENTE: siempre la suya; SUPER_ADMIN: la de X-Tenant-ID.
     *
     * @throws com.unisen.sgp.exception.EmpresaNoSeleccionadaException SUPER_ADMIN en modo global (→ 400)
     */
    @PreAuthorize(Permisos.GESTION)
    @Transactional(readOnly = true)
    public Page<TrabajadorResponseDTO> listarTrabajadores(EstadoUsuario estado, Pageable pageable) {
        Long empresaId = TenantContextHolder.requerirEmpresa();
        Page<Usuario> trabajadores = estado == null
                ? usuarioRepository.findByEmpresa_IdAndRol(empresaId, Rol.USUARIO, pageable)
                : usuarioRepository.findByEmpresa_IdAndRolAndEstado(empresaId, Rol.USUARIO, estado, pageable);
        return trabajadores.map(TrabajadorResponseDTO::from);
    }

    /**
     * Aprueba, rechaza, desactiva o reactiva a un trabajador de la empresa de la petición. El
     * cambio rige desde la siguiente petición del trabajador, aunque su JWT siga vigente: el
     * filtro JWT lee el estado de la BD cada vez.
     *
     * <p>Solo trabajadores (rol USUARIO): un gestor no cambia el estado de otro gestor ni el
     * suyo. Otra empresa, otro rol o un id inexistente responden igual (404), sin revelar nada.
     *
     * @throws RecursoNoEncontradoException si no es un trabajador de la empresa actual
     * @throws com.unisen.sgp.exception.ConflictoException si la transición no está permitida (409)
     */
    @PreAuthorize(Permisos.GESTION)
    @Transactional
    public TrabajadorResponseDTO cambiarEstadoTrabajador(Long id, CambioEstadoUsuarioDTO dto) {
        Long empresaId = TenantContextHolder.requerirEmpresa();
        UsuarioPrincipal gestor = UsuarioActual.obtener();
        Usuario trabajador = usuarioRepository.findWithLockByIdAndEmpresa_IdAndRol(id, empresaId, Rol.USUARIO)
                .orElseThrow(() -> new RecursoNoEncontradoException("trabajador", id));

        EstadoUsuario anterior = trabajador.getEstado();
        trabajador.cambiarEstado(dto.estado());
        Usuario guardado = usuarioRepository.saveAndFlush(trabajador);
        log.info("Trabajador {} de la empresa {}: {} → {} (por el usuario {}).", id, empresaId, anterior,
                guardado.getEstado(), gestor.getId());
        return TrabajadorResponseDTO.from(guardado);
    }

    private static void validarPassword(String password) {
        if (password == null || password.length() < PASSWORD_MIN_LENGTH) {
            throw new CampoInvalidoException("password",
                    "La contraseña debe tener al menos " + PASSWORD_MIN_LENGTH + " caracteres.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw new CampoInvalidoException("password",
                    "La contraseña no puede superar " + PASSWORD_MAX_BYTES + " bytes.");
        }
    }
}
