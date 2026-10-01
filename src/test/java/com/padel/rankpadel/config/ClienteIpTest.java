package com.padel.rankpadel.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("ClienteIp - de qué IP viene realmente una request")
class ClienteIpTest {

    private ClienteIp conProxies(int proxies) {
        ClienteIp clienteIp = new ClienteIp();
        ReflectionTestUtils.setField(clienteIp, "proxiesConfiables", proxies);
        return clienteIp;
    }

    private MockHttpServletRequest requestCon(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    @DisplayName("Un X-Forwarded-For inventado por el cliente no puede hacerse pasar por otra IP")
    void ignoraElValorQuePoneElCliente() {
        // El atacante manda su propio header; el proxy le agrega la IP real detrás. Si se
        // tomara el primer valor, con mandar uno distinto en cada intento estrenaba un
        // cubo de rate limit nuevo cada vez y el límite del login no servía para nada.
        String espiado = requestCon("1.2.3.4, 190.55.10.20").getHeader("X-Forwarded-For");
        assertThat(espiado).startsWith("1.2.3.4");

        assertThat(conProxies(1).de(requestCon("1.2.3.4, 190.55.10.20"))).isEqualTo("190.55.10.20");
    }

    @Test
    @DisplayName("Con la cadena limpia devuelve la IP del visitante")
    void tomaLaIpRealDelVisitante() {
        assertThat(conProxies(1).de(requestCon("190.55.10.20"))).isEqualTo("190.55.10.20");
    }

    @Test
    @DisplayName("Con dos proxies adelante salta el que agregó el de más afuera")
    void respetaLaCantidadDeProxiesConfiables() {
        // Cloudflare delante de Render: la IP del visitante es la anteúltima.
        assertThat(conProxies(2).de(requestCon("1.2.3.4, 190.55.10.20, 172.16.0.9")))
                .isEqualTo("190.55.10.20");
    }

    @Test
    @DisplayName("Sin encabezado, la IP de la conexión")
    void sinEncabezadoUsaLaConexion() {
        assertThat(conProxies(1).de(requestCon(null))).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("Un encabezado con más saltos de los esperados no rompe ni devuelve vacío")
    void cadenaMasCortaQueLosProxiesConfigurados() {
        assertThat(conProxies(3).de(requestCon("190.55.10.20"))).isEqualTo("190.55.10.20");
    }
}
