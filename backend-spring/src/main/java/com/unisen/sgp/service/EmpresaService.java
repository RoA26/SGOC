package com.unisen.sgp.service;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.exception.ConflictoException;
import com.unisen.sgp.exception.DatoDuplicadoException;
import com.unisen.sgp.exception.RecursoNoEncontradoException;
import com.unisen.sgp.model.dto.CambioEstadoEmpresaDTO;
import com.unisen.sgp.model.dto.CambioEstadoUsuarioDTO;
import com.unisen.sgp.model.dto.CodigoEmpresarialDTO;
import com.unisen.sgp.model.dto.EmpresaRequestDTO;
import com.unisen.sgp.model.dto.EmpresaResponseDTO;
import com.unisen.sgp.model.dto.TrabajadorResponseDTO;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.EstadoUsuario;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.EmpresaRepository;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.security.CodigosInvitacion;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.security.UsuarioActual;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Empresas cliente (tenants). La tabla es global: el SUPER_ADMIN las aprovisiona, consulta y
 * activa o desactiva; un gestor solo ve la suya.
 *
 * <p>Empresa y Usuario no llevan {@code @TenantId}: las operaciones del SUPER_ADMIN sobre una
 * empresa concreta la identifican por su id en la ruta y filtran por ella de forma explícita. El
 * resto de datos de la empresa (catálogos, solicitudes) se consultan con el mecanismo de tenants
 * existente ({@code X-Tenant-ID}).
 */
@Service
public class EmpresaService {

    private static final Logger log = LoggerFactory.getLogger(EmpresaService.class);
    /** Prefijo de los errores del gerente fundador: mismo formato que Bean Validation ({@code gerente.username}). */
    private static final String CAMPO_GERENTE = "gerente.";
    private static final String CAMPO_CODIGO = "codigo";
    /** Un código propuesto por el servidor solo choca si otra alta simultánea lo toma: basta con pocos reintentos. */
    private static final int MAX_INTENTOS_CODIGO_PROPUESTO = 3;

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final TransactionTemplate transactionTemplate;

    public EmpresaService(EmpresaRepository empresaRepository, UsuarioRepository usuarioRepository,
                          UsuarioService usuarioService, TransactionTemplate transactionTemplate) {
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Crea la empresa, su código empresarial, su código de registro de trabajadores y su GERENTE
     * fundador (ACTIVO) en una sola transacción: si cualquier paso falla (NIT o código repetido,
     * username o correo en uso, error de BD...), no queda ni la empresa ni el gerente.
     *
     * <p>Sin {@code codigo} en la petición, el servidor propone el siguiente libre a partir del
     * nombre. Si otra alta simultánea lo toma antes (la UNIQUE de la BD salta), se repite toda la
     * operación en una transacción nueva con el siguiente código: tras una violación de una
     * restricción, PostgreSQL ya no admite más sentencias en la transacción en curso. Un código
     * elegido por el SUPER_ADMIN nunca se cambia: si está ocupado, 409.
     *
     * <p>Se ejecuta en modo global (sin X-Tenant-ID): Empresa y Usuario son tablas globales,
     * sin {@code @TenantId}, así que no dependen del tenant de la sesión.
     *
     * @throws DatoDuplicadoException si el NIT ({@code nit}), el código ({@code codigo}) o el
     *         username/correo del gerente ({@code gerente.username} / {@code gerente.email}) ya existen (→ 409)
     */
    @PreAuthorize(Permisos.SUPER_ADMIN)
    public EmpresaResponseDTO crearConGerente(EmpresaRequestDTO request) {
        for (int intento = 1; ; intento++) {
            try {
                return transactionTemplate.execute(estado -> aprovisionar(request));
            } catch (DatoDuplicadoException ex) {
                boolean carreraPorCodigoPropuesto = CAMPO_CODIGO.equals(ex.getCampo()) && request.codigo() == null;
                if (!carreraPorCodigoPropuesto || intento >= MAX_INTENTOS_CODIGO_PROPUESTO) {
                    throw ex;
                }
                log.info("Código empresarial propuesto ocupado por un alta simultánea; reintento {}.", intento + 1);
            }
        }
    }

    /** Una transacción completa del aprovisionamiento (ver {@link #crearConGerente}). */
    private EmpresaResponseDTO aprovisionar(EmpresaRequestDTO request) {
        if (empresaRepository.existsByNit(request.nit())) {
            throw new DatoDuplicadoException("nit", "Ya existe una empresa con este NIT.");
        }
        String codigo = request.codigo() != null ? exigirDisponible(request.codigo()) : proponerCodigo(request.nombre());
        String codigoRegistro = CodigosInvitacion.generarUnico(empresaRepository::existsByCodigoEmpresa);

        // flush: la empresa ya está en la BD cuando se crea el gerente (FK y UNIQUE comprobadas aquí).
        Empresa empresa;
        try {
            empresa = empresaRepository.saveAndFlush(new Empresa(request.nombre(), request.nit(), codigo, codigoRegistro));
        } catch (DataIntegrityViolationException ex) {
            // Alta simultánea con el mismo NIT o el mismo código.
            throw duplicadoDeLaEmpresa(ex, codigo);
        }

        EmpresaRequestDTO.GerenteFundador datos = request.gerente();
        Usuario gerente;
        try {
            gerente = usuarioService.crearUsuario(datos.username(), datos.email(), datos.nombreParaMostrar(),
                    datos.password(), Rol.GERENTE, empresa);
        } catch (DatoDuplicadoException ex) {
            // La excepción deshace la transacción: la empresa tampoco se guarda.
            throw new DatoDuplicadoException(CAMPO_GERENTE + ex.getCampo(), ex.getMessage());
        } catch (CampoInvalidoException ex) {
            throw new CampoInvalidoException(CAMPO_GERENTE + ex.getCampo(), ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            // Alta simultánea con el mismo username o correo: la UNIQUE salta al insertar al gerente.
            throw duplicadoDelGerente(ex);
        }

        log.info("Empresa {} ({}) creada con el gerente fundador {} por {}.", empresa.getId(), empresa.getCodigo(),
                gerente.getId(), UsuarioActual.paraAuditoria());
        return EmpresaResponseDTO.from(empresa, gerente);
    }

    private String exigirDisponible(String codigo) {
        if (empresaRepository.existsByCodigo(codigo)) {
            throw codigoEnUso(codigo);
        }
        return codigo;
    }

    private String proponerCodigo(String nombre) {
        try {
            return CodigosEmpresariales.sugerir(nombre, empresaRepository::findCodigosConPrefijo);
        } catch (IllegalStateException ex) {
            throw new ConflictoException("No quedan códigos libres para este nombre. Indica el código manualmente.");
        }
    }

    private static DatoDuplicadoException codigoEnUso(String codigo) {
        return new DatoDuplicadoException(CAMPO_CODIGO, "El código " + codigo + " ya pertenece a otra empresa.");
    }

    /** Asocia la restricción violada al campo de la empresa; cualquier otra se propaga tal cual. */
    private static RuntimeException duplicadoDeLaEmpresa(DataIntegrityViolationException ex, String codigo) {
        String causa = causa(ex);
        // Antes que uq_empresas_codigo, que es un prefijo de su nombre.
        if (causa.contains("uq_empresas_codigo_empresa")) {
            return ex;
        }
        if (causa.contains("uq_empresas_codigo")) {
            return codigoEnUso(codigo);
        }
        if (causa.contains("uq_empresas_nit")) {
            return new DatoDuplicadoException("nit", "Ya existe una empresa con este NIT.");
        }
        return ex;
    }

    /** Asocia la restricción violada al campo del gerente; cualquier otra se propaga tal cual. */
    private static RuntimeException duplicadoDelGerente(DataIntegrityViolationException ex) {
        String causa = causa(ex);
        DatoDuplicadoException duplicado = causa.contains("uq_usuarios_username") ? DatoDuplicadoException.username()
                : causa.contains("uq_usuarios_email") ? DatoDuplicadoException.email()
                : null;
        return duplicado == null ? ex
                : new DatoDuplicadoException(CAMPO_GERENTE + duplicado.getCampo(), duplicado.getMessage());
    }

    private static String causa(DataIntegrityViolationException ex) {
        return String.valueOf(NestedExceptionUtils.getMostSpecificCause(ex).getMessage()).toLowerCase(Locale.ROOT);
    }

    /**
     * Código que se propondría hoy para una empresa con este nombre. Es orientativo: el alta lo
     * vuelve a calcular (o lo valida, si el SUPER_ADMIN lo envía) dentro de su transacción.
     */
    @PreAuthorize(Permisos.SUPER_ADMIN)
    @Transactional(readOnly = true)
    public CodigoEmpresarialDTO sugerirCodigo(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new CampoInvalidoException("nombre", "Indica el nombre de la empresa.");
        }
        return new CodigoEmpresarialDTO(proponerCodigo(nombre), true);
    }

    /**
     * Si un código tiene el formato correcto y está libre.
     *
     * @throws CampoInvalidoException si el formato no es válido (→ 400 en {@code errors.codigo})
     */
    @PreAuthorize(Permisos.SUPER_ADMIN)
    @Transactional(readOnly = true)
    public CodigoEmpresarialDTO comprobarCodigo(String codigo) {
        String normalizado = CodigosEmpresariales.normalizar(codigo);
        if (!CodigosEmpresariales.tieneFormatoValido(normalizado)) {
            throw new CampoInvalidoException(CAMPO_CODIGO,
                    "El código debe tener de 3 a 12 letras mayúsculas o números y empezar por una letra (p. ej. NASC01).");
        }
        return new CodigoEmpresarialDTO(normalizado, !empresaRepository.existsByCodigo(normalizado));
    }

    /** Todas las empresas, con su administrador principal (dos consultas por página, sin N+1). */
    @PreAuthorize(Permisos.SUPER_ADMIN)
    @Transactional(readOnly = true)
    public Page<EmpresaResponseDTO> listar(Pageable pageable) {
        Page<Empresa> empresas = empresaRepository.findAll(pageable);
        Map<Long, Usuario> administradores = administradoresPrincipales(empresas.getContent());
        return empresas.map(empresa -> EmpresaResponseDTO.from(empresa, administradores.get(empresa.getId())));
    }

    @PreAuthorize(Permisos.SUPER_ADMIN)
    @Transactional(readOnly = true)
    public EmpresaResponseDTO obtener(Long id) {
        Empresa empresa = buscar(id);
        return EmpresaResponseDTO.from(empresa, administradoresPrincipales(List.of(empresa)).get(id));
    }

    /**
     * Personas de una empresa (gerentes y trabajadores) con su estado, para la ficha del
     * SUPER_ADMIN. Solo lectura y solo de la empresa de la ruta.
     */
    @PreAuthorize(Permisos.SUPER_ADMIN)
    @Transactional(readOnly = true)
    public Page<TrabajadorResponseDTO> listarUsuarios(Long empresaId, Pageable pageable) {
        buscar(empresaId);
        return usuarioRepository.findByEmpresa_Id(empresaId, pageable).map(TrabajadorResponseDTO::from);
    }

    /**
     * Activa o desactiva una empresa. Al desactivarla, ninguno de sus usuarios puede operar desde
     * su siguiente petición (el filtro JWT lee el estado de la BD en cada una); sus datos se
     * conservan y el SUPER_ADMIN puede seguir consultándolos.
     *
     * @throws ConflictoException si ya estaba en ese estado (→ 409)
     */
    @PreAuthorize(Permisos.SUPER_ADMIN)
    @Transactional
    public EmpresaResponseDTO cambiarEstado(Long id, CambioEstadoEmpresaDTO dto) {
        Empresa empresa = buscar(id);
        boolean activa = dto.activa();
        if (empresa.isActiva() == activa) {
            throw new ConflictoException(activa ? "La empresa ya está activa." : "La empresa ya está desactivada.");
        }
        empresa.setActiva(activa);
        empresaRepository.saveAndFlush(empresa);
        log.info("Empresa {} ({}) {} por {}.", id, empresa.getCodigo(), activa ? "reactivada" : "desactivada",
                UsuarioActual.paraAuditoria());
        return EmpresaResponseDTO.from(empresa, administradoresPrincipales(List.of(empresa)).get(id));
    }

    /**
     * Desactiva o reactiva la cuenta de un GERENTE de la empresa (p. ej. el administrador
     * principal deja la empresa o su cuenta se ve comprometida). Rige desde su siguiente
     * petición, aunque su JWT siga vigente. Las transiciones son las de {@link EstadoUsuario}.
     *
     * <p>Solo gerentes de la empresa de la ruta: otra empresa, otro rol o un id inexistente
     * responden igual (404). La fila se bloquea frente a cambios simultáneos.
     */
    @PreAuthorize(Permisos.SUPER_ADMIN)
    @Transactional
    public TrabajadorResponseDTO cambiarEstadoGerente(Long empresaId, Long usuarioId, CambioEstadoUsuarioDTO dto) {
        Usuario gerente = usuarioRepository.findWithLockByIdAndEmpresa_IdAndRol(usuarioId, empresaId, Rol.GERENTE)
                .orElseThrow(() -> new RecursoNoEncontradoException("gerente", usuarioId));
        EstadoUsuario anterior = gerente.getEstado();
        gerente.cambiarEstado(dto.estado());
        Usuario guardado = usuarioRepository.saveAndFlush(gerente);
        log.info("Gerente {} de la empresa {}: {} → {} (por {}).", usuarioId, empresaId, anterior,
                guardado.getEstado(), UsuarioActual.paraAuditoria());
        return TrabajadorResponseDTO.from(guardado);
    }

    /**
     * Empresa de la petición, con el código que el gestor comparte con sus trabajadores.
     * GERENTE: la suya; SUPER_ADMIN: la de X-Tenant-ID.
     *
     * @throws com.unisen.sgp.exception.EmpresaNoSeleccionadaException SUPER_ADMIN en modo global (→ 400)
     */
    @PreAuthorize(Permisos.GESTION)
    @Transactional(readOnly = true)
    public EmpresaResponseDTO actual() {
        Long empresaId = TenantContextHolder.requerirEmpresa();
        return empresaRepository.findById(empresaId)
                .map(EmpresaResponseDTO::from)
                .orElseThrow(() -> new RecursoNoEncontradoException("empresa", empresaId));
    }

    private Empresa buscar(Long id) {
        return empresaRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("empresa", id));
    }

    /** Administrador principal (primer GERENTE) de cada empresa, por id de empresa. */
    private Map<Long, Usuario> administradoresPrincipales(List<Empresa> empresas) {
        if (empresas.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = empresas.stream().map(Empresa::getId).toList();
        return usuarioRepository.findPrimeroConRolPorEmpresa(Rol.GERENTE, ids).stream()
                .collect(Collectors.toMap(Usuario::getEmpresaId, Function.identity()));
    }
}
