package com.unisen.sgp.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CORS y cabeceras del proxy en la publicación en {@code n3xora.duckdns.org} sin retirar
 * {@code rrtf.duckdns.org}. Los valores son los que produce {@code docker-compose.yml} con
 * {@code APP_DOMAIN=n3xora.duckdns.org} y {@code CORS_ORIGENES_ADICIONALES=https://rrtf.duckdns.org}.
 */
class CorsTransicionDominioTest {

    static final String LOGIN = "/api/auth/login";
    static final String CREDENCIALES = "{\"username\":\"nadie.cors\",\"password\":\"incorrecta-123\"}";

    @Nested
    @SpringBootTest(properties = "app.security.cors.allowed-origins=https://n3xora.duckdns.org,https://rrtf.duckdns.org")
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class ConfiguracionDeTransicion {

        @Autowired
        MockMvc mockMvc;

        @Test
        void admiteElDominioNuevo() throws Exception {
            mockMvc.perform(options(LOGIN)
                            .header(HttpHeaders.ORIGIN, "https://n3xora.duckdns.org")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,x-tenant-id"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://n3xora.duckdns.org"))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        }

        @Test
        void sigueAdmitiendoElDominioAnterior() throws Exception {
            mockMvc.perform(options(LOGIN)
                            .header(HttpHeaders.ORIGIN, "https://rrtf.duckdns.org")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://rrtf.duckdns.org"));
        }

        @Test
        void rechazaElDominioNuevoSinHttps() throws Exception {
            mockMvc.perform(options(LOGIN)
                            .header(HttpHeaders.ORIGIN, "http://n3xora.duckdns.org")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void enProduccionNoAdmiteViteLocal() throws Exception {
            mockMvc.perform(options(LOGIN)
                            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void rechazaDominiosParecidos() throws Exception {
            for (String origen : new String[] {"https://n3xora.duckdns.org.example.com", "https://x-n3xora.duckdns.org",
                    "https://n3xora.duckdns.org:8443", "null"}) {
                mockMvc.perform(options(LOGIN)
                                .header(HttpHeaders.ORIGIN, origen)
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                        .andExpect(status().isForbidden())
                        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
            }
        }

        @Test
        void unPostDesdeUnOrigenAjenoSeRechazaAntesDeAutenticar() throws Exception {
            mockMvc.perform(post(LOGIN)
                            .header(HttpHeaders.ORIGIN, "https://malicioso.example.com")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREDENCIALES))
                    .andExpect(status().isForbidden());
        }
    }

    /**
     * Producción real: la SPA y la API comparten origen. Detrás del proxy TLS, el backend reconstruye
     * esquema y host con {@code X-Forwarded-Proto} y {@code X-Forwarded-Host} (Nginx del frontend), así
     * que la petición no es de CORS aunque el dominio no figure en la lista. Si el proxy no enviara
     * {@code X-Forwarded-Proto: https}, la petición parecería http y pasaría a depender de la lista:
     * por eso el dominio nuevo debe figurar en ella.
     */
    @Nested
    @SpringBootTest(properties = "app.security.cors.allowed-origins=https://rrtf.duckdns.org")
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class MismoOrigenTrasElProxy {

        @Autowired
        MockMvc mockMvc;

        @Test
        void conLasCabecerasDelProxyEsMismoOrigen() throws Exception {
            mockMvc.perform(post(LOGIN)
                            .header(HttpHeaders.HOST, "n3xora.duckdns.org")
                            .header("X-Forwarded-Proto", "https")
                            .header("X-Forwarded-Host", "n3xora.duckdns.org")
                            .header(HttpHeaders.ORIGIN, "https://n3xora.duckdns.org")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREDENCIALES))
                    // Llega al login (credenciales inválidas), no lo corta CORS.
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void sinXForwardedProtoDependeDeLaLista() throws Exception {
            mockMvc.perform(post(LOGIN)
                            .header(HttpHeaders.HOST, "n3xora.duckdns.org")
                            .header(HttpHeaders.ORIGIN, "https://n3xora.duckdns.org")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREDENCIALES))
                    .andExpect(status().isForbidden());
        }
    }
}
