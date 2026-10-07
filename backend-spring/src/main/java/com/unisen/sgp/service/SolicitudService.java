package com.unisen.sgp.service;

import static com.unisen.sgp.repository.SolicitudRepository.Filtros.conEstado;
import static com.unisen.sgp.repository.SolicitudRepository.Filtros.deSolicitante;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.exception.RecursoNoEncontradoException;
import com.unisen.sgp.model.dto.CambioEstadoSolicitudDTO;
import com.unisen.sgp.model.dto.DetalleSolicitudDTO;
import com.unisen.sgp.model.dto.SolicitudRequestDTO;
import com.unisen.sgp.model.dto.SolicitudResponseDTO;
import com.unisen.sgp.model.entity.EstadoSolicitud;
import com.unisen.sgp.model.entity.ProductoReferencia;
import com.unisen.sgp.model.entity.Solicitud;
import com.unisen.sgp.repository.ProductoReferenciaRepository;
import com.unisen.sgp.repository.SolicitudRepository;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.security.Permisos;
import com.unisen.sgp.security.UsuarioActual;
import com.unisen.sgp.security.UsuarioPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solicitudes internas de compra. Aplica la seguridad a nivel de fila: un USUARIO solo ve y
 * crea las suyas; ADMIN y GERENTE ven todas y las revisan.
 */
@Service
@Transactional(readOnly = true)
public class SolicitudService {

    private final SolicitudRepository solicitudRepository;
    private final ProductoReferenciaRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    public SolicitudService(SolicitudRepository solicitudRepository, ProductoReferenciaRepository productoRepository,
                            UsuarioRepository usuarioRepository, Clock clock) {
        this.solicitudRepository = solicitudRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
    }

    /**
     * Crea la solicitud con todas sus líneas en una sola transacción (todo o nada). El
     * solicitante es siempre el usuario autenticado y el estado, PENDIENTE: el cliente no
     * puede fijar ninguno de los dos.
     *
     * @throws CampoInvalidoException si un producto no existe, está dado de baja o se repite
     */
    @Transactional
    public SolicitudResponseDTO crear(SolicitudRequestDTO dto) {
        UsuarioPrincipal actual = UsuarioActual.obtener();
        Map<Long, ProductoReferencia> productos = productosActivos(dto.detalles());

        Solicitud solicitud = new Solicitud(usuarioRepository.getReferenceById(actual.getId()),
                dto.justificacion(), ahora());
        Set<Long> yaIncluidos = new HashSet<>();
        for (int i = 0; i < dto.detalles().size(); i++) {
            DetalleSolicitudDTO linea = dto.detalles().get(i);
            ProductoReferencia producto = productos.get(linea.productoId());
            if (producto == null) {
                throw new CampoInvalidoException(campoProducto(i), "El producto no existe o fue dado de baja.");
            }
            if (!yaIncluidos.add(producto.getId())) {
                throw new CampoInvalidoException(campoProducto(i),
                        "Este producto ya está en otra línea: ajusta allí la cantidad.");
            }
            solicitud.agregarDetalle(producto, linea.cantidad());
        }
        // Cascade: la cabecera y sus líneas se insertan juntas; flush para que cualquier
        // violación de restricción salte aquí, dentro de la transacción.
        return SolicitudResponseDTO.from(solicitudRepository.saveAndFlush(solicitud));
    }

    /** USUARIO: solo las suyas. ADMIN / GERENTE: todas. En ambos casos, filtrable por estado. */
    public Page<SolicitudResponseDTO> listar(EstadoSolicitud estado, Pageable pageable) {
        UsuarioPrincipal actual = UsuarioActual.obtener();
        Specification<Solicitud> filtro = conEstado(estado);
        if (!actual.getRol().esGestor()) {
            filtro = filtro.and(deSolicitante(actual.getId()));
        }
        return solicitudRepository.findAll(filtro, pageable).map(SolicitudResponseDTO::from);
    }

    /** Una solicitud ajena responde 404 a un USUARIO: no se revela que existe. */
    public SolicitudResponseDTO obtener(Long id) {
        UsuarioPrincipal actual = UsuarioActual.obtener();
        Solicitud solicitud = solicitudRepository.findById(id)
                .filter(s -> actual.getRol().esGestor() || s.perteneceA(actual.getId()))
                .orElseThrow(() -> RecursoNoEncontradoException.solicitud(id));
        return SolicitudResponseDTO.from(solicitud);
    }

    /**
     * Aprueba o rechaza una solicitud PENDIENTE. La fila se bloquea durante la transacción:
     * dos revisiones simultáneas se serializan y la segunda recibe 409.
     */
    @PreAuthorize(Permisos.GESTION)
    @Transactional
    public SolicitudResponseDTO cambiarEstado(Long id, CambioEstadoSolicitudDTO dto) {
        UsuarioPrincipal revisor = UsuarioActual.obtener();
        Solicitud solicitud = solicitudRepository.findWithLockById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.solicitud(id));
        solicitud.revisar(dto.estado(), usuarioRepository.getReferenceById(revisor.getId()), dto.comentario(), ahora());
        return SolicitudResponseDTO.from(solicitudRepository.saveAndFlush(solicitud));
    }

    /** Una sola consulta para todas las líneas; solo se pueden pedir productos activos. */
    private Map<Long, ProductoReferencia> productosActivos(List<DetalleSolicitudDTO> detalles) {
        Set<Long> ids = detalles.stream().map(DetalleSolicitudDTO::productoId).collect(Collectors.toSet());
        return productoRepository.findAllById(ids).stream()
                .filter(ProductoReferencia::isActivo)
                .collect(Collectors.toMap(ProductoReferencia::getId, Function.identity()));
    }

    /** Mismo formato que Bean Validation ({@code detalles[1].productoId}) para que el formulario marque la línea. */
    private static String campoProducto(int indice) {
        return "detalles[" + indice + "].productoId";
    }

    private Instant ahora() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
