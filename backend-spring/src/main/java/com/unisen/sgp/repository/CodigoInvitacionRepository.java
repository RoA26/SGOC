package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.CodigoInvitacion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface CodigoInvitacionRepository extends JpaRepository<CodigoInvitacion, Long> {

    /**
     * Busca el código bloqueando su fila ({@code SELECT … FOR UPDATE}) hasta el fin de la
     * transacción: dos registros simultáneos con el mismo código se serializan y el segundo
     * ya lo ve como usado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CodigoInvitacion> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);
}
