package com.unisen.sgp.service;

import com.unisen.sgp.exception.ModoGlobalRequeridoException;
import com.unisen.sgp.model.dto.AnaliticaDTO;
import com.unisen.sgp.model.dto.AnaliticaDTO.Compras;
import com.unisen.sgp.model.dto.AnaliticaDTO.Empresas;
import com.unisen.sgp.model.dto.AnaliticaDTO.Inventario;
import com.unisen.sgp.model.dto.AnaliticaDTO.OrdenesDeEmpresa;
import com.unisen.sgp.model.dto.AnaliticaDTO.OrdenesGlobales;
import com.unisen.sgp.model.dto.AnaliticaDTO.Recepciones;
import com.unisen.sgp.repository.AnaliticaRepository;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.security.UsuarioActual;
import com.unisen.sgp.service.CalendarioNegocio.Periodo;
import com.unisen.sgp.tenant.TenantContextHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indicadores de los dashboards, uno por contexto:
 *
 * <ul>
 *   <li><b>Global</b> (SUPER_ADMIN): todas las empresas. Exige el modo global explícito; con una
 *       empresa en el contexto se rechaza en lugar de devolver solo la suya.</li>
 *   <li><b>Empresa</b> (ADMIN_EMPRESA, o SUPER_ADMIN en supervisión): la empresa del contexto, que
 *       fija el backend; el cliente no puede elegir otra.</li>
 *   <li><b>Personal</b> (TRABAJADOR y ADMIN_EMPRESA): disponibilidad de productos y sus propias
 *       solicitudes.</li>
 * </ul>
 *
 * Todo se calcula con consultas agregadas en la BD en el momento (sin caché): un número fijo de
 * consultas por petición, independiente del volumen de datos.
 */
@Service
@Transactional(readOnly = true)
public class AnaliticaService {

    /** Empresas que muestra el ranking de actividad. */
    static final int TOP_EMPRESAS = 5;
    /** Productos que muestra la lista "requieren atención". */
    static final int PRODUCTOS_EN_ALERTA = 5;

    private final AnaliticaRepository repository;
    private final CalendarioNegocio calendario;

    public AnaliticaService(AnaliticaRepository repository, CalendarioNegocio calendario) {
        this.repository = repository;
        this.calendario = calendario;
    }

    @PreAuthorize(Permisos.SUPER_ADMIN)
    public AnaliticaDTO.Global global(LocalDate desde, LocalDate hasta) {
        exigirModoGlobal();
        Periodo periodo = calendario.periodo(desde, hasta);
        long[] empresas = repository.conteoEmpresas();
        return new AnaliticaDTO.Global(
                periodoDTO(periodo),
                new Empresas(empresas[0], empresas[1], empresas[0] - empresas[1]),
                new OrdenesGlobales(repository.ordenesEmitidas(periodo.inicio(), periodo.fin()), repository.totalOrdenes(),
                        repository.ordenesPorEstado()),
                repository.empresasConMasOrdenes(periodo.inicio(), periodo.fin(), TOP_EMPRESAS),
                calendario.serie(periodo, repository.ordenesEmitidasPorHora(periodo.inicio(), periodo.fin())));
    }

    @PreAuthorize(Permisos.SUPER_ADMIN)
    public Page<OrdenesDeEmpresa> ordenesPorEmpresa(LocalDate desde, LocalDate hasta, Pageable pageable) {
        exigirModoGlobal();
        Periodo periodo = calendario.periodo(desde, hasta);
        return repository.ordenesPorEmpresa(periodo.inicio(), periodo.fin(), pageable);
    }

    @PreAuthorize(Permisos.GESTION)
    public AnaliticaDTO.DeEmpresa deEmpresa(LocalDate desde, LocalDate hasta) {
        TenantContextHolder.requerirEmpresa();
        Periodo periodo = calendario.periodo(desde, hasta);
        long[] inventario = repository.estadoInventario();
        Object[] recepciones = repository.recepciones(periodo.inicio(), periodo.fin());
        return new AnaliticaDTO.DeEmpresa(
                periodoDTO(periodo),
                new Inventario(inventario[0], inventario[1], inventario[2], inventario[3],
                        repository.productosEnAlerta(PRODUCTOS_EN_ALERTA)),
                repository.solicitudesPorEstado(null),
                new Compras(repository.ordenesEmitidas(periodo.inicio(), periodo.fin()),
                        repository.valorComprometido(periodo.inicio(), periodo.fin()), (BigDecimal) recepciones[2],
                        repository.totalOrdenes(), repository.ordenesPorEstado()),
                new Recepciones((Long) recepciones[0], (Long) recepciones[1], repository.totalRecepciones(),
                        calendario.serie(periodo, repository.recepcionesPorHora(periodo.inicio(), periodo.fin()))));
    }

    @PreAuthorize(Permisos.MIEMBRO_EMPRESA)
    public AnaliticaDTO.Personal personal() {
        TenantContextHolder.requerirEmpresa();
        long[] inventario = repository.estadoInventario();
        return new AnaliticaDTO.Personal(inventario[1], inventario[3], inventario[2],
                repository.solicitudesPorEstado(UsuarioActual.obtener().getId()));
    }

    private AnaliticaDTO.Periodo periodoDTO(Periodo periodo) {
        return new AnaliticaDTO.Periodo(periodo.desde(), periodo.hasta(), calendario.zona().getId(),
                periodo.granularidad());
    }

    private static void exigirModoGlobal() {
        if (TenantContextHolder.obtener().isPresent()) {
            throw new ModoGlobalRequeridoException();
        }
    }
}
