package com.unisen.sgp.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.unisen.sgp.support.RelojMutable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Ventana deslizante en memoria: umbral, recuperación, concurrencia y memoria acotada. */
class LimiteDeIntentosTest {

    private static final Duration VENTANA = Duration.ofMinutes(15);

    private final RelojMutable reloj = new RelojMutable(Instant.parse("2026-10-22T12:00:00Z"));

    private void fallar(LimiteDeIntentos limite, String clave) {
        assertThat(limite.reservar(clave)).as("debería haber cupo").isEmpty();
        limite.liberar(clave, true);
    }

    @Test
    void cuentaSoloLoQueSeMarcaYBloqueaAlAlcanzarElUmbral() {
        LimiteDeIntentos limite = new LimiteDeIntentos(3, VENTANA, 100, reloj);
        assertThat(limite.reservar("ana")).isEmpty();
        limite.liberar("ana", false); // un intento que no cuenta (p. ej. un login correcto)
        assertThat(limite.contados("ana")).isZero();

        fallar(limite, "ana");
        fallar(limite, "ana");
        assertThat(limite.contados("ana")).isEqualTo(2);
        assertThat(limite.reservar("ana")).isEmpty();
        assertThat(limite.liberar("ana", true)).as("el tercero alcanza el límite").isTrue();

        assertThat(limite.reservar("ana")).contains(VENTANA);
        assertThat(limite.reservar("luis")).as("otra clave no se ve afectada").isEmpty();
    }

    @Test
    void seRecuperaAlCaducarCadaIntentoSinBloqueoPermanente() {
        LimiteDeIntentos limite = new LimiteDeIntentos(2, VENTANA, 100, reloj);
        fallar(limite, "ana");
        reloj.avanzar(Duration.ofMinutes(5));
        fallar(limite, "ana");

        assertThat(limite.reservar("ana")).contains(Duration.ofMinutes(10)); // hasta que caduque el primero
        reloj.avanzar(Duration.ofMinutes(10));
        assertThat(limite.contados("ana")).isEqualTo(1);
        assertThat(limite.reservar("ana")).as("caducó el primer fallo: vuelve a haber un cupo").isEmpty();
        limite.liberar("ana", true);
        assertThat(limite.reservar("ana")).contains(Duration.ofMinutes(5));

        reloj.avanzar(VENTANA);
        assertThat(limite.contados("ana")).isZero();
        assertThat(limite.claves()).as("sin intentos vigentes la clave se olvida").isZero();
    }

    @Test
    void lasReservasEnCursoOcupanCupoAunqueNoHayanTerminado() {
        LimiteDeIntentos limite = new LimiteDeIntentos(2, VENTANA, 100, reloj);
        assertThat(limite.reservar("ana")).isEmpty();
        assertThat(limite.reservar("ana")).isEmpty();
        assertThat(limite.reservar("ana")).as("dos en curso: no hay cupo").contains(Duration.ofSeconds(1));
        limite.liberar("ana", false);
        assertThat(limite.reservar("ana")).isEmpty();
    }

    @Test
    void muchasPeticionesSimultaneasNoSuperanElUmbral() throws Exception {
        for (int ronda = 0; ronda < 5; ronda++) {
            LimiteDeIntentos limite = new LimiteDeIntentos(5, VENTANA, 100, reloj);
            int hilos = 64;
            CountDownLatch salida = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(hilos);
            try {
                List<Future<Boolean>> futuros = new ArrayList<>();
                for (int i = 0; i < hilos; i++) {
                    futuros.add(executor.submit(() -> {
                        salida.await();
                        Optional<Duration> espera = limite.reservar("ana");
                        if (espera.isPresent()) {
                            return false;
                        }
                        Thread.sleep(5); // la operación protegida tarda algo
                        limite.liberar("ana", true);
                        return true;
                    }));
                }
                salida.countDown();
                long admitidos = 0;
                for (Future<Boolean> futuro : futuros) {
                    admitidos += futuro.get(30, TimeUnit.SECONDS) ? 1 : 0;
                }
                assertThat(admitidos).as("ronda %d", ronda).isEqualTo(5);
                assertThat(limite.contados("ana")).isEqualTo(5);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void laMemoriaQuedaAcotadaYSeLiberaAlCaducar() {
        LimiteDeIntentos limite = new LimiteDeIntentos(3, VENTANA, 100, reloj);
        for (int i = 0; i < 1000; i++) {
            String clave = "usuario-" + i;
            if (limite.reservar(clave).isEmpty()) {
                limite.liberar(clave, true);
            }
        }
        assertThat(limite.claves()).isLessThanOrEqualTo(100);

        reloj.avanzar(VENTANA);
        fallar(limite, "nuevo"); // al llenarse, se purgan las caducadas antes de rechazar sitio
        assertThat(limite.claves()).isEqualTo(1);
    }

    @Test
    void reiniciarOlvidaTodo() {
        LimiteDeIntentos limite = new LimiteDeIntentos(1, VENTANA, 100, reloj);
        fallar(limite, "ana");
        assertThat(limite.reservar("ana")).isPresent();
        limite.reiniciar();
        assertThat(limite.reservar("ana")).isEmpty();
    }
}
