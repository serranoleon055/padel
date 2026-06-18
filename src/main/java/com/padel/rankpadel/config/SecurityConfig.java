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
                        .requestMatchers(HttpMethod.GET, "/api/reservas/disponibilidad").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/reservas").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/reservas/lote").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/torneos/*/inscripciones").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/pagos/reserva").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/pagos/inscripcion").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/pagos/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/pagos/*").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(
                                HttpStatus.UNAUTHORIZED.value(), "No autenticado")))
                .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
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
