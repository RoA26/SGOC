package com.unisen.sgp.config;

import com.unisen.sgp.security.JwtAuthenticationFilter;
import com.unisen.sgp.security.JwtUtil;
import com.unisen.sgp.security.RestAccessDeniedHandler;
import com.unisen.sgp.security.RestAuthenticationEntryPoint;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    public static final String LOGIN_PATH = "/api/auth/login";
    /** Alta pública, pero solo prospera con un código de invitación válido. */
    public static final String REGISTRO_PATH = "/api/auth/registro";

    private static final String[] SWAGGER_PATHS = {"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};
    private static final String[] HEALTH_PATHS = {"/actuator/health", "/actuator/health/**"};

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtUtil jwtUtil,
            UserDetailsService userDetailsService,
            AuthenticationManager authenticationManager,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {

        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtUtil, userDetailsService);

        return http
                // API sin estado autenticada por header Bearer: sin cookies de sesión no hay vector CSRF.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                // La cadena usa el mismo AuthenticationManager que el login (ver authenticationManager()).
                .authenticationManager(authenticationManager)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, LOGIN_PATH, REGISTRO_PATH).permitAll()
                        .requestMatchers(HEALTH_PATHS).permitAll()
                        .requestMatchers(SWAGGER_PATHS).permitAll()
                        // Necesario para que los errores reenviados a /error no se conviertan en 401.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder(SecurityProperties securityProperties) {
        return new BCryptPasswordEncoder(securityProperties.bcryptStrength());
    }

    /**
     * Autenticación usuario/contraseña contra la BD.
     *
     * <p>Por defecto Spring comprueba si la cuenta está deshabilitada <em>antes</em> de
     * verificar la contraseña, lo que revela a cualquiera que una cuenta existe y está
     * inactiva. Aquí el estado se comprueba <em>después</em>: sin la contraseña correcta,
     * la respuesta es siempre "credenciales inválidas". Para usuarios inexistentes,
     * {@link DaoAuthenticationProvider} ya iguala los tiempos de respuesta.
     */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setPreAuthenticationChecks(user -> { });
        provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
        return new ProviderManager(provider);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(SecurityProperties securityProperties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(securityProperties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        // El token viaja en un header, no en cookies.
        config.setAllowCredentials(false);
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
