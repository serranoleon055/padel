package com.padel.rankpadel.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final LoginRateLimitFilter loginRateLimitFilter;
    private final PublicWriteRateLimitFilter publicWriteRateLimitFilter;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://127.0.0.1:3000}")
    private String allowedOrigins;

    @Bean
    AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(contentType -> {
                        })
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/jugadores/*/ficha").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/jugadores/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categorias/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/torneos/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/home/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/partidos/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/lugares/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/temporadas/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/plantillas-formato/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/plantillas-puntos/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/ranking/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/canchas/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/promociones-cancha").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/configuracion-sede").permitAll()
                        // La franja de auspiciantes la ve el jugador; administrarlos, no.
                        .requestMatchers(HttpMethod.GET, "/api/sponsors").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reservas/disponibilidad").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/reservas").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/reservas/lote").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/torneos/*/inscripciones").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/pagos/reserva").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/pagos/inscripcion").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/pagos/webhook").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/pagos/*/cancelar").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/pagos/*").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()

                        // ── Solo el dueño ──────────────────────────────────────────
                        // Lo que el del mostrador no tiene por qué ver ni poder hacer.
                        // El criterio: los números del negocio, la plata que sale, y
                        // todo lo que sirva para tapar un faltante.
                        //
                        // Reabrir un cierre es el caso más claro: si el que cobró puede
                        // reabrir su propio arqueo, la firma de V51 no vale nada.
                        .requestMatchers(HttpMethod.DELETE, "/api/caja/cierre").hasRole("DUENIO")
                        .requestMatchers("/api/estadisticas/**").hasRole("DUENIO")
                        .requestMatchers("/api/gastos/**").hasRole("DUENIO")
                        .requestMatchers("/api/admins/**").hasRole("DUENIO")
                        // Vender necesita leer el catálogo; cambiar precios, comprar
                        // mercadería, ajustar o dar de baja stock, no.
                        .requestMatchers(HttpMethod.POST, "/api/productos/**").hasRole("DUENIO")
                        .requestMatchers(HttpMethod.PUT, "/api/productos/**").hasRole("DUENIO")
                        .requestMatchers(HttpMethod.DELETE, "/api/productos/**").hasRole("DUENIO")
                        .requestMatchers("/api/proveedores/**").hasRole("DUENIO")
                        .requestMatchers(HttpMethod.DELETE, "/api/torneos/**").hasRole("DUENIO")
                        .requestMatchers(HttpMethod.PUT, "/api/configuracion-sede").hasRole("DUENIO")
                        .requestMatchers(HttpMethod.POST, "/api/configuracion-sede/**").hasRole("DUENIO")
                        .requestMatchers("/api/promociones-cancha/**").hasRole("DUENIO")
                        // El horario define qué se vende y a qué hora: es la misma
                        // decisión que el precio, y el mostrador no la toma. Leerlo sí
                        // puede: la grilla de turnos lo necesita para ordenar la jornada.
                        .requestMatchers(HttpMethod.POST, "/api/horarios-cancha/**").hasRole("DUENIO")
                        .requestMatchers("/api/sponsors/**").hasRole("DUENIO")
                        .requestMatchers("/api/importar/**").hasRole("DUENIO")

                        .anyRequest().hasRole("ADMIN"))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(
                                HttpStatus.UNAUTHORIZED.value(), "No autenticado")))
                .addFilterBefore(publicWriteRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(loginRateLimitFilter, PublicWriteRateLimitFilter.class)
                .addFilterBefore(jwtFilter, LoginRateLimitFilter.class);

        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
