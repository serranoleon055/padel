package com.padel.rankpadel.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.enums.RolUsuario;
import com.padel.rankpadel.util.JwtUtil;

/**
 * Las reglas de rol de {@link SecurityConfig}, una por una.
 *
 * <p>Hasta acá ninguna tenía cobertura, y por eso el hueco de {@code GET /api/caja} —que
 * le mostraba al empleado los gastos y la rentabilidad del club por una pantalla que sí
 * puede abrir— vivió sin que nada lo marcara. Este es el test que lo habría cazado.
 *
 * <p>No hace falta ningún controller: la autorización se evalúa por ruta, antes de llegar
 * al método. Un 403 significa "denegado" y un 404 significa "autorizado, pero esa ruta no
 * existe en este contexto de prueba", que para esta pregunta es lo mismo que un 200. Por
 * eso se afirma sobre el 401 y el 403, y no sobre el código exacto.
 */
@WebMvcTest(controllers = SeguridadEndpointsTest.ControladorDePrueba.class)
@Import({ SecurityConfig.class, JwtFilter.class, LoginRateLimitFilter.class,
        PublicWriteRateLimitFilter.class, ClienteIp.class, JwtUtil.class })
@TestPropertySource(properties = {
        "jwt.secret=cmFua3BhZGVsLXRlc3Qta2V5LWRlLXRyZWludGEteS1kb3MtY2hhcnMtbWluaW1v",
        "jwt.expiration-ms=86400000",
        "app.cors.allowed-origins=http://localhost:5173"
})
@DisplayName("SecurityConfig - quién puede entrar a cada endpoint")
class SeguridadEndpointsTest {

    /** Existe solo para que la prueba no escanee los controllers de verdad. */
    @RestController
    @RequestMapping("/__prueba")
    static class ControladorDePrueba {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    /** Rutas que solo puede tocar el dueño. */
    static Stream<Caso> soloDuenio() {
        return Stream.of(
                new Caso(HttpMethod.GET, "/api/caja/cierres"),
                new Caso(HttpMethod.DELETE, "/api/caja/cierre?fecha=2026-08-15"),
                new Caso(HttpMethod.GET, "/api/estadisticas"),
                new Caso(HttpMethod.GET, "/api/gastos?desde=2026-08-01&hasta=2026-08-31"),
                new Caso(HttpMethod.POST, "/api/gastos"),
                new Caso(HttpMethod.GET, "/api/admins"),
                new Caso(HttpMethod.POST, "/api/productos"),
                new Caso(HttpMethod.PUT, "/api/productos/1"),
                new Caso(HttpMethod.DELETE, "/api/productos/1"),
                new Caso(HttpMethod.POST, "/api/productos/1/compras"),
                new Caso(HttpMethod.POST, "/api/productos/1/presentaciones"),
                new Caso(HttpMethod.PUT, "/api/productos/presentaciones/1"),
                new Caso(HttpMethod.DELETE, "/api/productos/presentaciones/1"),
                new Caso(HttpMethod.PATCH, "/api/productos/presentaciones/1/reactivar"),
                new Caso(HttpMethod.GET, "/api/proveedores"),
                new Caso(HttpMethod.POST, "/api/compras"),
                new Caso(HttpMethod.GET, "/api/compras"),
                new Caso(HttpMethod.GET, "/api/compras/1"),
                new Caso(HttpMethod.DELETE, "/api/compras/1"),
                new Caso(HttpMethod.GET, "/api/proveedores/1/cuenta"),
                new Caso(HttpMethod.POST, "/api/proveedores/1/pagos"),
                // Aplicar un conteo da de baja mercadería sin que nadie la haya vendido.
                new Caso(HttpMethod.POST, "/api/recuentos"),
                new Caso(HttpMethod.GET, "/api/recuentos"),
                new Caso(HttpMethod.GET, "/api/recuentos/en-curso"),
                new Caso(HttpMethod.PUT, "/api/recuentos/1/conteo"),
                new Caso(HttpMethod.POST, "/api/recuentos/1/aplicar"),
                new Caso(HttpMethod.DELETE, "/api/recuentos/1"),
                new Caso(HttpMethod.DELETE, "/api/torneos/1"),
                new Caso(HttpMethod.PUT, "/api/configuracion-sede"),
                new Caso(HttpMethod.POST, "/api/promociones-cancha"),
                new Caso(HttpMethod.POST, "/api/horarios-cancha"),
                new Caso(HttpMethod.POST, "/api/sponsors"),
                new Caso(HttpMethod.POST, "/api/importar/clientes"),
                // Anular un movimiento de caja, por lo mismo que reabrir un cierre.
                new Caso(HttpMethod.DELETE, "/api/movimientos-caja/1"));
    }

    /** Rutas que el empleado del mostrador también usa. */
    static Stream<Caso> tambienElMostrador() {
        return Stream.of(
                // El arqueo lo cuenta el empleado: el endpoint no se le puede cerrar. Lo
                // que se le poda es el contenido, y eso lo cubre CajaServiceTest.
                new Caso(HttpMethod.GET, "/api/caja"),
                new Caso(HttpMethod.POST, "/api/caja/cierre"),
                new Caso(HttpMethod.GET, "/api/productos"),
                // El mostrador necesita leer las presentaciones para saber como cobrar.
                new Caso(HttpMethod.GET, "/api/productos/1/presentaciones"),
                new Caso(HttpMethod.POST, "/api/ventas"),
                new Caso(HttpMethod.GET, "/api/reservas/jornada-actual"),
                new Caso(HttpMethod.GET, "/api/clientes"),
                // Leer el horario sí: la grilla de turnos lo necesita para ordenar la
                // jornada. Cargarlo es decisión del dueño y está en la lista de arriba.
                new Caso(HttpMethod.GET, "/api/horarios-cancha"),
                // Abrir la caja y cargar un movimiento los puede el empleado: es el que
                // arranca el día. Qué CONCEPTOS puede cargar lo decide el servicio,
                // porque depende del cuerpo del pedido y no de la ruta.
                new Caso(HttpMethod.POST, "/api/movimientos-caja/apertura"),
                new Caso(HttpMethod.POST, "/api/movimientos-caja"),
                new Caso(HttpMethod.GET, "/api/movimientos-caja"));
    }

    /** Lo que tiene que andar sin estar logueado, porque lo usa el sitio público. */
    static Stream<Caso> publicas() {
        return Stream.of(
                new Caso(HttpMethod.GET, "/api/reservas/disponibilidad-sede?lugarId=1&fecha=2026-08-15"),
                new Caso(HttpMethod.GET, "/api/reservas/disponibilidad?canchaId=1&fecha=2026-08-15"),
                new Caso(HttpMethod.GET, "/api/ranking"),
                new Caso(HttpMethod.GET, "/api/canchas"),
                new Caso(HttpMethod.GET, "/api/configuracion-sede"),
                new Caso(HttpMethod.GET, "/api/sponsors"),
                // El turno del jugador: lo autoriza el token del enlace, no una sesión.
                new Caso(HttpMethod.GET, "/api/reservas/mio/abc-123"),
                new Caso(HttpMethod.PATCH, "/api/reservas/mio/abc-123/cancelar"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("soloDuenio")
    @DisplayName("Solo el dueño: al mostrador lo rechaza")
    void soloDuenio_elMostradorRecibe403(Caso caso) throws Exception {
        assertThat(estado(caso, RolUsuario.MOSTRADOR)).isEqualTo(403);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("soloDuenio")
    @DisplayName("Solo el dueño: al dueño lo deja pasar")
    void soloDuenio_elDuenioPasa(Caso caso) throws Exception {
        assertThat(estado(caso, RolUsuario.DUENIO)).isNotIn(401, 403);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("soloDuenio")
    @DisplayName("Solo el dueño: sin token no se entra")
    void soloDuenio_sinTokenRecibe401(Caso caso) throws Exception {
        assertThat(estado(caso, null)).isEqualTo(401);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tambienElMostrador")
    @DisplayName("El mostrador puede")
    void mostrador_pasa(Caso caso) throws Exception {
        assertThat(estado(caso, RolUsuario.MOSTRADOR)).isNotIn(401, 403);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("publicas")
    @DisplayName("El sitio público anda sin token")
    void publicas_andanSinToken(Caso caso) throws Exception {
        assertThat(estado(caso, null)).isNotIn(401, 403);
    }

    /**
     * @param rol null para pedir sin token
     * @return el código HTTP, que es lo único que decide esta capa
     */
    private int estado(Caso caso, RolUsuario rol) throws Exception {
        MockHttpServletRequestBuilder pedido = MockMvcRequestBuilders
                .request(caso.metodo(), caso.ruta())
                .contentType("application/json")
                .content("{}");
        if (rol != null) {
            pedido = pedido.header("Authorization", "Bearer " + jwtUtil.generateToken("admin", rol));
        }
        return mockMvc.perform(pedido).andReturn().getResponse().getStatus();
    }

    record Caso(HttpMethod metodo, String ruta) {
        @Override
        public String toString() {
            return metodo + " " + ruta;
        }
    }
}
