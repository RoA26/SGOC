package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.Empresa;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Empresas (tenants). Tabla global: no la filtra el tenant actual. */
public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    boolean existsByNit(String nit);

    Optional<Empresa> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoEmpresa(String codigoEmpresa);

    /**
     * Códigos empresariales que empiezan por {@code prefijo}, para proponer el siguiente libre.
     * El prefijo solo contiene A-Z y 0-9 ({@code CodigosEmpresariales.prefijo}): ningún comodín de LIKE.
     */
    @Query("select e.codigo from Empresa e where e.codigo like concat(:prefijo, '%')")
    List<String> findCodigosConPrefijo(@Param("prefijo") String prefijo);

    /**
     * Bloquea la fila de la empresa hasta el fin de la transacción: serializa la numeración
     * correlativa de sus documentos (órdenes de compra). En PostgreSQL es {@code FOR NO KEY
     * UPDATE}, compatible con las FK que la referencian al insertar.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Empresa> findWithLockById(Long id);

    /** Para el registro de trabajadores: el código se busca ya normalizado. */
    Optional<Empresa> findByCodigoEmpresa(String codigoEmpresa);
}
