package com.unisen.sgp.service;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.exception.DatoDuplicadoException;
import com.unisen.sgp.exception.RecursoNoEncontradoException;
import com.unisen.sgp.model.dto.EmpresaRequestDTO;
import com.unisen.sgp.model.dto.EmpresaResponseDTO;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.EmpresaRepository;
import com.unisen.sgp.security.CodigosInvitacion;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Empresas cliente (tenants). La tabla es global: el SUPER_ADMIN las aprovisiona y consulta;
 * un gestor solo ve la suya.
 */
@Service
public class EmpresaService {

    private static final Logger log = LoggerFactory.getLogger(EmpresaService.class);
    /** Prefijo de los errores del gerente fundador: mismo formato que Bean Validation ({@code gerente.username}). */
    private static final String CAMPO_GERENTE = "gerente.";

    private final EmpresaRepository empresaRepository;
    private final UsuarioService usuarioService;

    public EmpresaService(EmpresaRepository empresaRepository, UsuarioService usuarioService) {
        this.empresaRepository = empresaRepository;
        this.usuarioService = usuarioService;
    }

    /**
     * Crea la empresa, su código permanente y su GERENTE fundador (ACTIVO) en una sola
     * transacción: si cualquier paso falla (NIT repetido, username o correo en uso, error de
     * BD...), no queda ni la empresa ni el gerente.
     *
     * <p>Se ejecuta en modo global (sin X-Tenant-ID): Empresa y Usuario son tablas globales,
     * sin {@code @TenantId}, así que no dependen del tenant de la sesión.
     *
     * @throws DatoDuplicadoException si el NIT ({@code nit}) o el username/correo del gerente
     *         ({@code gerente.username} / {@code gerente.email}) ya existen (→ 409)
     */
    @PreAuthorize(Permisos.SUPER_ADMIN)
    @Transactional
    public EmpresaResponseDTO crearConGerente(EmpresaRequestDTO request) {
        if (empresaRepository.existsByNit(request.nit())) {
            throw new DatoDuplicadoException("nit", "Ya existe una empresa con este NIT.");
        }
        String codigo = CodigosInvitacion.generarUnico(empresaRepository::existsByCodigoEmpresa);
        // flush: la empresa ya está en la BD cuando se crea el gerente (FK y UNIQUE comprobadas aquí).
        Empresa empresa = empresaRepository.saveAndFlush(new Empresa(request.nombre(), request.nit(), codigo));

        EmpresaRequestDTO.GerenteFundador datos = request.gerente();
        Usuario gerente;
        try {
            gerente = usuarioService.crearUsuario(datos.username(), datos.email(), datos.nombreParaMostrar(),
                    datos.password(), Rol.GERENTE, empresa);
        } catch (DatoDuplicadoException ex) {
            // La excepción ya marcó la transacción para rollback: la empresa tampoco se guarda.
            throw new DatoDuplicadoException(CAMPO_GERENTE + ex.getCampo(), ex.getMessage());
        } catch (CampoInvalidoException ex) {
            throw new CampoInvalidoException(CAMPO_GERENTE + ex.getCampo(), ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            // Alta simultánea con el mismo username o correo: la UNIQUE salta al insertar al gerente.
            throw duplicadoDelGerente(ex);
        }

        log.info("Empresa {} creada con el gerente fundador {}.", empresa.getId(), gerente.getId());
        return EmpresaResponseDTO.from(empresa, gerente);
    }

    /** Asocia la restricción violada al campo del gerente; cualquier otra se propaga tal cual. */
    private static RuntimeException duplicadoDelGerente(DataIntegrityViolationException ex) {
        String causa = String.valueOf(NestedExceptionUtils.getMostSpecificCause(ex).getMessage())
                .toLowerCase(Locale.ROOT);
        DatoDuplicadoException duplicado = causa.contains("uq_usuarios_username") ? DatoDuplicadoException.username()
                : causa.contains("uq_usuarios_email") ? DatoDuplicadoException.email()
                : null;
        return duplicado == null ? ex
                : new DatoDuplicadoException(CAMPO_GERENTE + duplicado.getCampo(), duplicado.getMessage());
    }

    @PreAuthorize(Permisos.SUPER_ADMIN)
    @Transactional(readOnly = true)
    public Page<EmpresaResponseDTO> listar(Pageable pageable) {
        return empresaRepository.findAll(pageable).map(EmpresaResponseDTO::from);
    }

    @PreAuthorize(Permisos.SUPER_ADMIN)
    @Transactional(readOnly = true)
    public EmpresaResponseDTO obtener(Long id) {
        return empresaRepository.findById(id)
                .map(EmpresaResponseDTO::from)
                .orElseThrow(() -> new RecursoNoEncontradoException("empresa", id));
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
}
