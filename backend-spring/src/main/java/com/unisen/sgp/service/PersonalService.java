package com.unisen.sgp.service;

import com.unisen.sgp.model.dto.PersonalDTO;
import com.unisen.sgp.model.dto.PersonalDTO.Actividad;
import com.unisen.sgp.model.dto.PersonalDTO.TipoOperacion;
import com.unisen.sgp.model.dto.PersonalDTO.UltimaOperacion;
import com.unisen.sgp.model.dto.TrabajadorResponseDTO;
import com.unisen.sgp.model.entity.EstadoUsuario;
import com.unisen.sgp.repository.ActividadPorUsuario;
import com.unisen.sgp.repository.RecepcionRepository;
import com.unisen.sgp.repository.SolicitudRepository;
import com.unisen.sgp.repository.VentaRepository;
import com.unisen.sgp.security.Permisos;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vista de personal del administrador: los trabajadores de su empresa con el resumen de lo que
 * firmaron. Reutiliza el listado de {@link UsuarioService} (misma autorización y misma empresa) y
 * añade la actividad con una consulta agregada por fuente para toda la página, sin N+1.
 *
 * <p>Solo datos verificables: la última operación registrada y los conteos recientes. No hay
 * registro de inicios de sesión, así que no se informa ninguna "última conexión".
 */
@Service
@Transactional(readOnly = true)
public class PersonalService {

    /** Ventana de los conteos de actividad reciente. */
    public static final int DIAS_RECIENTES = 30;

    private final UsuarioService usuarioService;
    private final SolicitudRepository solicitudRepository;
    private final RecepcionRepository recepcionRepository;
    private final VentaRepository ventaRepository;
    private final Clock clock;

    public PersonalService(UsuarioService usuarioService, SolicitudRepository solicitudRepository,
                           RecepcionRepository recepcionRepository, VentaRepository ventaRepository, Clock clock) {
        this.usuarioService = usuarioService;
        this.solicitudRepository = solicitudRepository;
        this.recepcionRepository = recepcionRepository;
        this.ventaRepository = ventaRepository;
        this.clock = clock;
    }

    /**
     * Trabajadores de la empresa de la petición (ADMINISTRADOR_COMERCIAL: la suya; SUPER_ADMIN: la
     * de X-Tenant-ID) con su actividad. Los ids salen del listado del servidor y las operaciones
     * se filtran además por la empresa actual (@TenantId): nunca se mezcla otra empresa.
     */
    @PreAuthorize(Permisos.GESTION)
    public Page<PersonalDTO> listar(EstadoUsuario estado, Pageable pageable) {
        Page<TrabajadorResponseDTO> trabajadores = usuarioService.listarTrabajadores(estado, pageable);
        if (trabajadores.isEmpty()) {
            return trabajadores.map(t -> PersonalDTO.from(t, null));
        }
        List<Long> ids = trabajadores.map(TrabajadorResponseDTO::id).toList();
        Instant desde = clock.instant().minus(Duration.ofDays(DIAS_RECIENTES));
        Map<Long, ActividadPorUsuario> solicitudes = porUsuario(solicitudRepository.actividadPorUsuario(ids, desde));
        Map<Long, ActividadPorUsuario> recepciones = porUsuario(recepcionRepository.actividadPorUsuario(ids, desde));
        Map<Long, ActividadPorUsuario> ventas = porUsuario(ventaRepository.actividadPorUsuario(ids, desde));
        return trabajadores.map(t -> PersonalDTO.from(t,
                actividad(solicitudes.get(t.id()), recepciones.get(t.id()), ventas.get(t.id()))));
    }

    private static Map<Long, ActividadPorUsuario> porUsuario(List<ActividadPorUsuario> filas) {
        return filas.stream().collect(Collectors.toMap(ActividadPorUsuario::getUsuarioId, Function.identity()));
    }

    /** Cualquiera de las tres puede ser null (ninguna operación de ese tipo). */
    private static Actividad actividad(ActividadPorUsuario solicitudes, ActividadPorUsuario recepciones,
                                       ActividadPorUsuario ventas) {
        UltimaOperacion ultima = null;
        ultima = masReciente(ultima, TipoOperacion.SOLICITUD, solicitudes);
        ultima = masReciente(ultima, TipoOperacion.RECEPCION, recepciones);
        ultima = masReciente(ultima, TipoOperacion.VENTA, ventas);
        return new Actividad(ultima, DIAS_RECIENTES, recientes(solicitudes), recientes(recepciones), recientes(ventas));
    }

    private static UltimaOperacion masReciente(UltimaOperacion actual, TipoOperacion tipo, ActividadPorUsuario fila) {
        if (fila == null || fila.getUltima() == null) {
            return actual;
        }
        return actual == null || fila.getUltima().isAfter(actual.en()) ? new UltimaOperacion(tipo, fila.getUltima()) : actual;
    }

    private static long recientes(ActividadPorUsuario fila) {
        return fila == null || fila.getRecientes() == null ? 0 : fila.getRecientes();
    }
}
