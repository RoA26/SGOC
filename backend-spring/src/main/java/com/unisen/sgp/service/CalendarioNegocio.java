package com.unisen.sgp.service;

import com.unisen.sgp.config.NegocioProperties;
import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.model.dto.AnaliticaDTO.Granularidad;
import com.unisen.sgp.model.dto.AnaliticaDTO.Punto;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.zone.ZoneOffsetTransition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Días del negocio para la analítica. Los instantes se guardan en UTC; un "día" es el de la zona
 * configurada ({@code app.negocio.zona-horaria}, Colombia por defecto).
 *
 * <p>Agrupar por día con funciones de zona de la BD no es portable (H2, la BD de los tests, no
 * aplica bien {@code AT TIME ZONE}). Por eso la BD agrupa por <em>hora UTC</em>
 * ({@code floor(extract(epoch) / 3600)}, idéntico en PostgreSQL y H2) y aquí cada hora se asigna
 * a su día local. Es exacto si los desfases de la zona son horas completas, como en Colombia; se
 * comprueba al arrancar.
 */
@Component
public class CalendarioNegocio {

    /** Período máximo de una consulta: un año (bisiesto incluido). */
    public static final int DIAS_MAXIMOS = 366;
    /** Hasta tres meses se muestra la evolución por día; por encima, por mes. */
    static final int DIAS_MAXIMOS_POR_DIA = 92;
    /** Período por defecto: los últimos 30 días, hoy incluido. */
    static final int DIAS_POR_DEFECTO = 30;
    private static final LocalDate FECHA_MINIMA = LocalDate.of(2000, 1, 1);
    private static final long SEGUNDOS_POR_HORA = 3600;

    private final ZoneId zona;
    private final Clock clock;

    public CalendarioNegocio(NegocioProperties properties, Clock clock) {
        this.zona = properties.zonaHoraria();
        this.clock = clock;
        comprobarDesfasesEnHorasCompletas(zona, clock.instant());
    }

    /** Período validado de [desde, hasta] (fechas locales, ambas incluidas). */
    public record Periodo(LocalDate desde, LocalDate hasta, Instant inicio, Instant fin, Granularidad granularidad) {
    }

    public ZoneId zona() {
        return zona;
    }

    public LocalDate hoy() {
        return LocalDate.now(clock.withZone(zona));
    }

    /**
     * Sin fechas: los últimos 30 días. Solo con {@code desde}: hasta hoy. Solo con {@code hasta}:
     * los 30 días que terminan ese día.
     *
     * @throws CampoInvalidoException si el período es incoherente, futuro o supera 366 días
     */
    public Periodo periodo(LocalDate desde, LocalDate hasta) {
        LocalDate hoy = hoy();
        LocalDate fin = hasta != null ? hasta : hoy;
        LocalDate inicio = desde != null ? desde : fin.minusDays(DIAS_POR_DEFECTO - 1L);
        if (fin.isAfter(hoy)) {
            throw new CampoInvalidoException("hasta", "La fecha final no puede ser posterior a hoy.");
        }
        if (inicio.isBefore(FECHA_MINIMA)) {
            throw new CampoInvalidoException("desde", "La fecha inicial no puede ser anterior al 01/01/2000.");
        }
        if (inicio.isAfter(fin)) {
            throw new CampoInvalidoException("desde", "La fecha inicial no puede ser posterior a la final.");
        }
        long dias = ChronoUnit.DAYS.between(inicio, fin) + 1;
        if (dias > DIAS_MAXIMOS) {
            throw new CampoInvalidoException("desde", "El período no puede superar " + DIAS_MAXIMOS + " días.");
        }
        return new Periodo(inicio, fin, inicio.atStartOfDay(zona).toInstant(), fin.plusDays(1).atStartOfDay(zona).toInstant(),
                dias <= DIAS_MAXIMOS_POR_DIA ? Granularidad.DIA : Granularidad.MES);
    }

    /**
     * Serie completa del período a partir de conteos por hora UTC (hora = segundos epoch / 3600):
     * un punto por día (o por mes) y 0 donde no hubo actividad. Los ceros son reales: el período
     * se consultó entero.
     */
    public List<Punto> serie(Periodo periodo, Map<Long, Long> conteoPorHora) {
        Map<LocalDate, Long> porGrupo = new TreeMap<>();
        for (LocalDate dia = periodo.desde(); !dia.isAfter(periodo.hasta()); dia = dia.plusDays(1)) {
            porGrupo.putIfAbsent(grupo(dia, periodo.granularidad()), 0L);
        }
        conteoPorHora.forEach((hora, conteo) -> {
            LocalDate dia = Instant.ofEpochSecond(hora * SEGUNDOS_POR_HORA).atZone(zona).toLocalDate();
            porGrupo.merge(grupo(dia, periodo.granularidad()), conteo, Long::sum);
        });
        List<Punto> puntos = new ArrayList<>(porGrupo.size());
        porGrupo.forEach((fecha, valor) -> puntos.add(new Punto(fecha, valor)));
        return puntos;
    }

    private static LocalDate grupo(LocalDate dia, Granularidad granularidad) {
        return granularidad == Granularidad.DIA ? dia : dia.withDayOfMonth(1);
    }

    /**
     * Las horas UTC solo se asignan bien a días locales si la zona no usa desfases con minutos
     * (p. ej. UTC+5:30). Se comprueba el desfase actual y el de la próxima transición, si la hay.
     */
    static void comprobarDesfasesEnHorasCompletas(ZoneId zona, Instant ahora) {
        var reglas = zona.getRules();
        List<Integer> desfases = new ArrayList<>(List.of(reglas.getOffset(ahora).getTotalSeconds()));
        ZoneOffsetTransition siguiente = reglas.nextTransition(ahora);
        if (siguiente != null) {
            desfases.add(siguiente.getOffsetAfter().getTotalSeconds());
        }
        if (desfases.stream().anyMatch(segundos -> segundos % SEGUNDOS_POR_HORA != 0)) {
            throw new IllegalStateException("app.negocio.zona-horaria=" + zona
                    + " tiene un desfase que no es de horas completas; la analítica agrupa por hora UTC.");
        }
    }
}
