package com.unisen.sgp.service;

import com.unisen.sgp.exception.RecursoNoEncontradoException;
import com.unisen.sgp.model.dto.InvitacionResponseDTO;
import com.unisen.sgp.model.dto.InvitacionResumenDTO;
import com.unisen.sgp.model.entity.CodigoInvitacion;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.CodigoInvitacionRepository;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.security.CodigosInvitacion;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invitaciones de trabajadores de la empresa de la petición: generarlas, listarlas y revocarlas.
 * Solo gestores (ADMIN_EMPRESA de su empresa, o SUPER_ADMIN con X-Tenant-ID) y siempre con una
 * empresa concreta en el contexto; {@code @TenantId} limita cada consulta a esa empresa, así que una
 * invitación de otra empresa no existe para quien la pide (404). El canje vive en {@link AuthService}.
 */
@Service
public class InvitacionService {

    private static final Logger log = LoggerFactory.getLogger(InvitacionService.class);

    public static final int HORAS_VALIDEZ_POR_DEFECTO = 72;

    private final CodigoInvitacionRepository codigoInvitacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    public InvitacionService(CodigoInvitacionRepository codigoInvitacionRepository, UsuarioRepository usuarioRepository,
                             Clock clock) {
        this.codigoInvitacionRepository = codigoInvitacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
    }

    /**
     * Genera un código de un solo uso para la empresa actual: quien lo canjee será TRABAJADOR
     * ACTIVO de esa empresa.
     *
     * @param creadorId    gestor que genera el código (queda registrado)
     * @param horasValidez horas hasta la caducidad; {@code null} = {@value #HORAS_VALIDEZ_POR_DEFECTO}
     * @return el código en claro (única vez que se devuelve) y su fecha de caducidad
     */
    @PreAuthorize(Permisos.GESTION)
    @Transactional
    public InvitacionResponseDTO generar(Long creadorId, Integer horasValidez) {
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

    /** Invitaciones de la empresa actual con su estado, sin el código completo. */
    @PreAuthorize(Permisos.GESTION)
    @Transactional(readOnly = true)
    public Page<InvitacionResumenDTO> listar(Pageable pageable) {
        TenantContextHolder.requerirEmpresa();
        Instant ahora = clock.instant();
        return codigoInvitacionRepository.findAllBy(pageable).map(invitacion -> InvitacionResumenDTO.from(invitacion, ahora));
    }

    /**
     * Revoca una invitación vigente de la empresa actual. La fila se bloquea: si un canje
     * simultáneo la consume antes, la revocación responde 409; si la revocación gana, el canje
     * posterior la ve revocada.
     *
     * @throws RecursoNoEncontradoException si no existe en la empresa actual (→ 404)
     * @throws com.unisen.sgp.exception.ConflictoException si ya se usó, ya estaba revocada o caducó (→ 409)
     */
    @PreAuthorize(Permisos.GESTION)
    @Transactional
    public InvitacionResumenDTO revocar(Long id, Long revocadorId) {
        Long empresaId = TenantContextHolder.requerirEmpresa();
        CodigoInvitacion invitacion = codigoInvitacionRepository.findWithLockById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("invitación", id));
        Instant ahora = clock.instant().truncatedTo(ChronoUnit.MICROS);
        invitacion.revocar(usuarioRepository.getReferenceById(revocadorId), ahora);
        codigoInvitacionRepository.saveAndFlush(invitacion);
        log.info("Invitación {} de la empresa {} revocada por el usuario {}.", id, empresaId, revocadorId);
        return InvitacionResumenDTO.from(invitacion, ahora);
    }
}
