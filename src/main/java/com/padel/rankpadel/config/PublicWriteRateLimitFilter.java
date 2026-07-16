package com.padel.rankpadel.config;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limita por IP los POST públicos de escritura (reservas, inscripciones, pagos).
 * El webhook de Mercado Pago queda afuera: viene de los servidores de MP y
 * perder una notificación es peor que el spam. Los admins autenticados tampoco
 * se limitan (el JwtFilter corre antes y deja la autenticación en el contexto).
 */
@Component
public class PublicWriteRateLimitFilter extends OncePerRequestFilter {

    private static final Pattern RUTAS_PUBLICAS_ESCRITURA = Pattern.compile(
            "^/api/(reservas(/lote)?|torneos/\\d+/inscripciones|pagos/(reserva|inscripcion|[^/]+/cancelar))$");

    private static final int MAX_ENTRADAS_MAPA = 10_000;

    @Value("${app.security.public-write-rate-limit.max-attempts:20}")
    private int maxAttempts;

    @Value("${app.security.public-write-rate-limit.window-seconds:60}")
    private long windowSeconds;

    private final ConcurrentHashMap<String, Window> intentosPorIp = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!esEscrituraPublica(request) || esAdminAutenticado()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (superaLimite(clientIp(request))) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\","
                            + "\"mensaje\":\"Demasiadas solicitudes seguidas. "
                            + "Esperá un momento e intentá de nuevo.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean esEscrituraPublica(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                && RUTAS_PUBLICAS_ESCRITURA.matcher(request.getServletPath()).matches();
    }

    private boolean esAdminAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private boolean superaLimite(String ip) {
        long ahora = System.currentTimeMillis();
        long ventanaMs = windowSeconds * 1000L;

        if (intentosPorIp.size() > MAX_ENTRADAS_MAPA) {
            intentosPorIp.entrySet().removeIf(e -> ahora - e.getValue().inicio >= ventanaMs);
        }

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
