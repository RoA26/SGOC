package com.unisen.sgp.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj de pruebas que solo avanza cuando se le pide (ventanas de tiempo sin esperas reales). */
public final class RelojMutable extends Clock {

    private volatile Instant ahora;

    public RelojMutable(Instant inicio) {
        this.ahora = inicio;
    }

    public void avanzar(Duration tiempo) {
        ahora = ahora.plus(tiempo);
    }

    @Override
    public Instant instant() {
        return ahora;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
