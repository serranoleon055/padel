package com.padel.rankpadel.config;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limita la cantidad de intentos de login por IP para mitigar ataques de
 * fuerza bruta sobre {@code POST /auth/login}.
 *
 * <p>Usa una ventana fija en memoria (sin dependencias externas). Para un
 * despliegue con múltiples instancias conviene migrar a un store compartido
 * (Redis) o a bucket4j; para un MVP de instancia única es suficiente.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/auth/login";

    @Value("${app.security.login-rate-limit.max-attempts:10}")
    private int maxAttempts;

    @Value("${app.security.login-rate-limit.window-seconds:60}")
    private long windowSeconds;

    private final ConcurrentHashMap<String, Window> intentosPorIp = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!esLoginPost(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        if (superaLimite(ip)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\","
                            + "\"mensaje\":\"Demasiados intentos de inicio de sesión. "
                            + "Esperá un momento e intentá de nuevo.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean esLoginPost(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                && LOGIN_PATH.equals(request.getServletPath());
    }

    /** Devuelve true si la IP superó el máximo de intentos en la ventana actual. */
    private boolean superaLimite(String ip) {
        long ahora = System.currentTimeMillis();
        long ventanaMs = windowSeconds * 1000L;

        Window ventana = intentosPorIp.compute(ip, (clave, actual) -> {
            if (actual == null || ahora - actual.inicio >= ventanaMs) {
                return new Window(ahora);
            }
            return actual;
        });

        return ventana.contador.incrementAndGet() > maxAttempts;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // El primer valor es la IP del cliente original.
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        private final long inicio;
        private final AtomicInteger contador = new AtomicInteger(0);

        private Window(long inicio) {
            this.inicio = inicio;
        }
    }
}
