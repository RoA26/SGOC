package com.unisen.sgp.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** Reloj inyectable: permite probar expiraciones de forma determinista. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
