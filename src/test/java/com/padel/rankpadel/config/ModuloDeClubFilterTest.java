package com.padel.rankpadel.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("ModuloDeClubFilter - lo que no existe en una instalación de organizador")
class ModuloDeClubFilterTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/api/reservas", "/api/reservas/disponibilidad-sede", "/api/turnos-fijos/1",
            "/api/caja", "/api/caja/cierre", "/api/cobros/1", "/api/gastos",
            "/api/movimientos-caja/apertura", "/api/ventas", "/api/productos/1/presentaciones",
            "/api/proveedores", "/api/compras", "/api/recuentos/en-curso", "/api/clientes",
            "/api/promociones-cancha", "/api/horarios-cancha", "/api/estadisticas",
            "/api/pagos/reserva", "/api/home/admin-dashboard" })
    @DisplayName("Organizador: los módulos de club responden 404")
    void organizador_bloqueaModulosDeClub(String ruta) throws Exception {
        MockHttpServletResponse respuesta = pedir("ORGANIZADOR", ruta);

        assertThat(respuesta.getStatus()).isEqualTo(404);
        assertThat(respuesta.getContentAsString()).contains("no está disponible");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/api/torneos", "/api/torneos/1/inscripciones", "/api/pagos/inscripcion",
            "/api/pagos/abc-123", "/api/lugares", "/api/canchas", "/api/ranking",
            "/api/home", "/api/home/summary", "/api/sponsors", "/api/configuracion-sede",
            // Que el prefijo se parezca no alcanza: se corta en la barra.
            "/api/reservasx" })
    @DisplayName("Organizador: lo de torneos sigue andando")
    void organizador_dejaPasarTorneos(String ruta) throws Exception {
        assertThat(pedir("ORGANIZADOR", ruta).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Club: no toca nada")
    void club_dejaPasarTodo() throws Exception {
        assertThat(pedir("CLUB", "/api/reservas").getStatus()).isEqualTo(200);
        assertThat(pedir("club", "/api/caja").getStatus()).isEqualTo(200);
    }

    private static MockHttpServletResponse pedir(String modo, String ruta) throws Exception {
        ModuloDeClubFilter filtro = new ModuloDeClubFilter(new Instalacion(modo));
        MockHttpServletRequest pedido = new MockHttpServletRequest("GET", ruta);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(pedido, respuesta, new MockFilterChain());
        return respuesta;
    }
}
