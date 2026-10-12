package com.padel.rankpadel.config;

import java.io.IOException;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * En una instalación de organizador, los módulos que solo tienen sentido para un club
 * (turnos, caja, mostrador, stock, clientes) no existen: responden 404.
 *
 * <p>El front ya no los ofrece, pero esconder un botón no apaga un endpoint. Sin esto,
 * {@code POST /api/reservas} —que es público— seguiría aceptando turnos en un sistema que
 * no tiene a nadie para atenderlos.
 *
 * <p>Corre después de Spring Security (sin {@code @Order}, queda último), así que la
 * respuesta sale con los encabezados de CORS y un pedido sin sesión a una ruta de admin
 * sigue recibiendo el 401 de siempre.
 */
@Component
public class ModuloDeClubFilter extends OncePerRequestFilter {

    private static final Pattern RUTAS_DE_CLUB = Pattern.compile(
            "^/api/(reservas|turnos-fijos|caja|cobros|gastos|movimientos-caja|ventas|productos"
                    + "|proveedores|compras|recuentos|clientes|promociones-cancha|horarios-cancha"
                    + "|estadisticas|pagos/reserva|home/admin-dashboard)(/.*)?$");

    private final Instalacion instalacion;

    public ModuloDeClubFilter(Instalacion instalacion) {
        this.instalacion = instalacion;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !instalacion.esOrganizador() || !esRutaDeClub(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.getWriter().write("{\"status\":404,\"error\":\"Not Found\","
                + "\"mensaje\":\"Esta función no está disponible en este sistema\"}");
    }

    static boolean esRutaDeClub(String ruta) {
        return ruta != null && RUTAS_DE_CLUB.matcher(ruta).matches();
    }
}
