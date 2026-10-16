package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.CodigoInvitacion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/**
 * Códigos de invitación. Son entidades de empresa ({@code @TenantId}): con un tenant en el
 * contexto, todas las consultas (también por id) se limitan a esa empresa.
 */
public interface CodigoInvitacionRepository extends JpaRepository<CodigoInvitacion, Long> {

    /**
     * Busca el código bloqueando su fila ({@code SELECT … FOR UPDATE}) hasta el fin de la
     * transacción: dos registros simultáneos con el mismo código se serializan y el segundo
     * ya lo ve como usado. Se usa en el registro público, sin tenant: el código decide la empresa.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CodigoInvitacion> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    /** Para revocar: bloquea la fila frente a un canje simultáneo. Solo de la empresa del contexto. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CodigoInvitacion> findWithLockById(Long id);

    /** Listado de la empresa del contexto, con creador, canjeador y revocador en la misma consulta. */
    @EntityGraph(attributePaths = {"creador", "usadoPor", "revocadoPor"})
    Page<CodigoInvitacion> findAllBy(Pageable pageable);
}
