package com.unisen.sgp.repository;

import com.unisen.sgp.model.entity.Empresa;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Empresas (tenants). Tabla global: no la filtra el tenant actual. */
public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    boolean existsByNit(String nit);

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoEmpresa(String codigoEmpresa);

    /**
     * Códigos empresariales que empiezan por {@code prefijo}, para proponer el siguiente libre.
     * El prefijo solo contiene A-Z y 0-9 ({@code CodigosEmpresariales.prefijo}): ningún comodín de LIKE.
     */
    @Query("select e.codigo from Empresa e where e.codigo like concat(:prefijo, '%')")
    List<String> findCodigosConPrefijo(@Param("prefijo") String prefijo);

    /** Para el registro de trabajadores: el código se busca ya normalizado. */
    Optional<Empresa> findByCodigoEmpresa(String codigoEmpresa);
}
