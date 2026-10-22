package com.unisen.sgp.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Límite de intentos por clave con ventana deslizante, en memoria. Cada intento contado caduca
 * por separado al cumplirse la ventana, así que el límite se recupera solo y nunca bloquea de
 * forma permanente.
 *
 * <p>Uso en dos pasos: {@link #reservar} antes de la operación y {@link #liberar} al terminar,
 * indicando si el intento cuenta. Las reservas en curso ocupan cupo, de modo que muchas peticiones
 * simultáneas con la misma clave no pueden superar el umbral. Todas las operaciones sobre una
 * clave son atómicas ({@link ConcurrentHashMap#compute}).
 *
 * <p>Memoria acotada: las claves sin intentos vigentes se eliminan y se vigilan del orden de
 * {@code maxClaves} a la vez (cota aproximada bajo concurrencia). Si se llena con intentos
 * vigentes, una clave nueva no se vigila (se prioriza la disponibilidad; queda documentado como
 * limitación). No coordina varias instancias y se vacía al reiniciar el proceso.
 */
public final class LimiteDeIntentos {

    /** Cada cuántas reservas se purgan las claves caducadas. */
    private static final int PURGA_CADA = 1024;
    /** Espera mínima informada cuando el cupo lo ocupan solo intentos aún en curso. */
    private static final Duration ESPERA_MINIMA = Duration.ofSeconds(1);

    private final int maximo;
    private final Duration ventana;
    private final int maxClaves;
    private final Clock clock;
    private final ConcurrentHashMap<String, Estado> estados = new ConcurrentHashMap<>();
    private final AtomicInteger reservas = new AtomicInteger();

    public LimiteDeIntentos(int maximo, Duration ventana, int maxClaves, Clock clock) {
        if (maximo < 1 || ventana.isNegative() || ventana.isZero() || maxClaves < 1) {
            throw new IllegalArgumentException("Límite no válido: máximo " + maximo + ", ventana " + ventana);
        }
        this.maximo = maximo;
        this.ventana = ventana;
        this.maxClaves = maxClaves;
        this.clock = clock;
    }

    /**
     * Reserva un intento para la clave.
     *
     * @return vacío si hay cupo (hay que llamar después a {@link #liberar}); si no, cuánto falta
     *         para que caduque el intento más antiguo (en ese caso no se reserva nada)
     */
    public Optional<Duration> reservar(String clave) {
        if (reservas.incrementAndGet() % PURGA_CADA == 0) {
            purgar();
        }
        if (!estados.containsKey(clave) && estados.size() >= maxClaves) {
            purgar();
            if (estados.size() >= maxClaves) {
                return Optional.empty(); // Sin sitio para vigilarla: se permite (ver la documentación de la clase).
            }
        }
        Duration[] espera = new Duration[1];
        estados.compute(clave, (k, estado) -> {
            Instant ahora = clock.instant();
            Estado actual = estado == null ? new Estado() : estado;
            actual.descartarCaducados(ahora, ventana);
            if (actual.contados.size() + actual.enCurso >= maximo) {
                espera[0] = actual.esperaHastaCupo(ahora, ventana);
            } else {
                actual.enCurso++;
            }
            return actual.vacio() ? null : actual;
        });
        return Optional.ofNullable(espera[0]);
    }

    /**
     * Cierra una reserva hecha con {@link #reservar}.
     *
     * @param cuenta si el intento consume cupo durante la ventana
     * @return {@code true} si con este intento la clave alcanza el límite (para registrarlo una vez)
     */
    public boolean liberar(String clave, boolean cuenta) {
        boolean[] alcanzado = new boolean[1];
        estados.computeIfPresent(clave, (k, estado) -> {
            Instant ahora = clock.instant();
            estado.enCurso = Math.max(0, estado.enCurso - 1);
            estado.descartarCaducados(ahora, ventana);
            if (cuenta) {
                estado.contados.addLast(ahora);
                alcanzado[0] = estado.contados.size() == maximo;
            }
            return estado.vacio() ? null : estado;
        });
        return alcanzado[0];
    }

    /** Intentos contados y vigentes de la clave (para pruebas y diagnóstico). */
    public int contados(String clave) {
        int[] vigentes = new int[1];
        estados.computeIfPresent(clave, (k, estado) -> {
            estado.descartarCaducados(clock.instant(), ventana);
            vigentes[0] = estado.contados.size();
            return estado.vacio() ? null : estado;
        });
        return vigentes[0];
    }

    /** Claves vigiladas ahora mismo. */
    public int claves() {
        return estados.size();
    }

    public int maximo() {
        return maximo;
    }

    public Duration ventana() {
        return ventana;
    }

    /** Olvida todos los intentos (pruebas automatizadas). */
    public void reiniciar() {
        estados.clear();
    }

    private void purgar() {
        Instant ahora = clock.instant();
        estados.keySet().forEach(clave -> estados.computeIfPresent(clave, (k, estado) -> {
            estado.descartarCaducados(ahora, ventana);
            return estado.vacio() ? null : estado;
        }));
    }

    /** Estado de una clave; solo se modifica dentro de {@code compute} (bloqueo por clave). */
    private static final class Estado {
        private final ArrayDeque<Instant> contados = new ArrayDeque<>();
        private int enCurso;

        void descartarCaducados(Instant ahora, Duration ventana) {
            while (!contados.isEmpty() && !contados.peekFirst().plus(ventana).isAfter(ahora)) {
                contados.removeFirst();
            }
        }

        Duration esperaHastaCupo(Instant ahora, Duration ventana) {
            if (contados.isEmpty()) {
                return ESPERA_MINIMA;
            }
            Duration espera = Duration.between(ahora, contados.peekFirst().plus(ventana));
            return espera.compareTo(ESPERA_MINIMA) < 0 ? ESPERA_MINIMA : espera;
        }

        boolean vacio() {
            return contados.isEmpty() && enCurso == 0;
        }
    }
}
