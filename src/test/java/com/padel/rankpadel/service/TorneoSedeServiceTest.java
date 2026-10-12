package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.padel.rankpadel.dto.FranjaSedeDto;
import com.padel.rankpadel.dto.request.PagoSedeRequest;
import com.padel.rankpadel.dto.request.TorneoSedeRequest;
import com.padel.rankpadel.dto.response.TorneoSedeResponse;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.Lugar;
import com.padel.rankpadel.entity.PagoSede;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.entity.TorneoSede;
import com.padel.rankpadel.enums.MedioPago;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.LugarRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.TorneoRepository;
import com.padel.rankpadel.repository.TorneoSedeRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("TorneoSedeService - clubes del torneo y lo que se les paga")
class TorneoSedeServiceTest {

    @Mock private TorneoSedeRepository torneoSedeRepository;
    @Mock private TorneoRepository torneoRepository;
    @Mock private LugarRepository lugarRepository;
    @Mock private CanchaRepository canchaRepository;
    @Mock private PartidoRepository partidoRepository;

    @InjectMocks
    private TorneoSedeService service;

    private Lugar principal;
    private Lugar otroClub;
    private Torneo torneo;
    private Cancha cancha1;
    private Cancha canchaAjena;

    @BeforeEach
    void setUp() {
        principal = Lugar.builder().id(1L).nombre("Top Padel").build();
        otroClub = Lugar.builder().id(2L).nombre("La Blanqueada").build();
        torneo = Torneo.builder().id(10L).nombre("Copa").lugar(principal).build();
        cancha1 = Cancha.builder().id(21L).nombre("Cancha 1").lugar(otroClub).activo(true).build();
        canchaAjena = Cancha.builder().id(11L).nombre("Cancha 1").lugar(principal).activo(true).build();
        comoDuenio();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void comoDuenio() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("ana", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_DUENIO"))));
    }

    private TorneoSedeRequest pedido(Long lugarId, List<Long> canchas, BigDecimal monto) {
        TorneoSedeRequest request = new TorneoSedeRequest();
        request.setLugarId(lugarId);
        request.setCanchaIds(canchas);
        request.setMontoAcordado(monto);
        return request;
    }

    @Nested
    @DisplayName("Agregar un club")
    class Agregar {

        @Test
        @DisplayName("Sin canchas elegidas usa todas las activas del club")
        void todasLasCanchas() {
            when(torneoRepository.findById(10L)).thenReturn(Optional.of(torneo));
            when(lugarRepository.findById(2L)).thenReturn(Optional.of(otroClub));
            when(canchaRepository.findByLugarIdAndActivoTrue(2L)).thenReturn(List.of(cancha1));

            TorneoSedeResponse respuesta = service.agregar(10L, pedido(2L, List.of(), new BigDecimal("150000")));

            assertThat(respuesta.getCanchas()).extracting(TorneoSedeResponse.CanchaSede::getId).containsExactly(21L);
            assertThat(respuesta.getSaldo()).isEqualByComparingTo("150000");
            assertThat(respuesta.isPrincipal()).isFalse();
        }

        @Test
        @DisplayName("No acepta canchas de otro club")
        void canchaDeOtroClub() {
            when(torneoRepository.findById(10L)).thenReturn(Optional.of(torneo));
            when(lugarRepository.findById(2L)).thenReturn(Optional.of(otroClub));
            when(canchaRepository.findAllById(any())).thenReturn(List.of(canchaAjena));

            assertThatThrownBy(() -> service.agregar(10L, pedido(2L, List.of(11L), null)))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("La Blanqueada");
            verify(torneoSedeRepository, never()).save(any());
        }

        @Test
        @DisplayName("El mismo club no se agrega dos veces")
        void duplicado() {
            when(torneoRepository.findById(10L)).thenReturn(Optional.of(torneo));
            when(lugarRepository.findById(2L)).thenReturn(Optional.of(otroClub));
            when(torneoSedeRepository.existsByTorneoIdAndLugarId(10L, 2L)).thenReturn(true);

            assertThatThrownBy(() -> service.agregar(10L, pedido(2L, List.of(), null)))
                    .isInstanceOf(EstadoInvalidoException.class);
        }
    }

    @Nested
    @DisplayName("Franjas")
    class Franjas {

        @Test
        @DisplayName("Rechaza una franja que termina antes de empezar o que pisa a otra")
        void invalidas() {
            LocalDate sabado = LocalDate.of(2026, 11, 7);
            assertThatThrownBy(() -> TorneoSedeService.validarFranjas(List.of(
                    new FranjaSedeDto(sabado, LocalTime.of(20, 0), LocalTime.of(18, 0)))))
                    .isInstanceOf(EstadoInvalidoException.class);
            assertThatThrownBy(() -> TorneoSedeService.validarFranjas(List.of(
                    new FranjaSedeDto(sabado, LocalTime.of(9, 0), LocalTime.of(14, 0)),
                    new FranjaSedeDto(sabado, LocalTime.of(13, 0), LocalTime.of(18, 0)))))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("se pisan");
        }

        @Test
        @DisplayName("Ordena por día y hora")
        void ordenadas() {
            LocalDate sabado = LocalDate.of(2026, 11, 7);
            var franjas = TorneoSedeService.validarFranjas(List.of(
                    new FranjaSedeDto(sabado.plusDays(1), LocalTime.of(9, 0), LocalTime.of(13, 0)),
                    new FranjaSedeDto(sabado, LocalTime.of(14, 0), LocalTime.of(20, 0)),
                    new FranjaSedeDto(sabado, LocalTime.of(9, 0), LocalTime.of(13, 0))));
            assertThat(franjas).extracting(f -> f.getFecha().getDayOfMonth() + " " + f.getHoraDesde())
                    .containsExactly("7 09:00", "7 14:00", "8 09:00");
        }
    }

    @Nested
    @DisplayName("Pagos al club")
    class Pagos {

        private TorneoSede sede;

        @BeforeEach
        void sede() {
            sede = TorneoSede.builder().id(5L).torneo(torneo).lugar(otroClub).montoAcordado(new BigDecimal("100000")).build();
            sede.getPagos().add(PagoSede.builder().id(1L).sede(sede).monto(new BigDecimal("60000")).build());
            lenient().when(torneoSedeRepository.findById(5L)).thenReturn(Optional.of(sede));
        }

        private PagoSedeRequest pago(String monto) {
            PagoSedeRequest request = new PagoSedeRequest();
            request.setFecha(LocalDate.now());
            request.setMonto(new BigDecimal(monto));
            request.setMedio(MedioPago.TRANSFERENCIA);
            return request;
        }

        @Test
        @DisplayName("Paga el saldo y queda en cero")
        void pagaElSaldo() {
            TorneoSedeResponse respuesta = service.registrarPago(10L, 5L, pago("40000"));

            assertThat(respuesta.getTotalPagado()).isEqualByComparingTo("100000");
            assertThat(respuesta.getSaldo()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("No deja pagar más que el saldo")
        void noPagaDeMas() {
            assertThatThrownBy(() -> service.registrarPago(10L, 5L, pago("40001")))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("40000");
        }

        @Test
        @DisplayName("Un pago anulado vuelve a sumar al saldo")
        void anular() {
            TorneoSedeResponse respuesta = service.anularPago(10L, 5L, 1L, "se cargó dos veces");

            assertThat(respuesta.getSaldo()).isEqualByComparingTo("100000");
            assertThat(respuesta.getPagos().get(0).isAnulado()).isTrue();
            assertThat(respuesta.getPagos().get(0).getAnuladoPor()).isEqualTo("ana");
        }

        @Test
        @DisplayName("Con pagos hechos no se puede quitar el club")
        void noSeQuitaConPagos() {
            assertThatThrownBy(() -> service.quitar(10L, 5L))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("anulá los pagos");
        }

        @Test
        @DisplayName("El mostrador ve el club pero no la plata")
        void mostradorSinMontos() {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("leo", null,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_MOSTRADOR"))));
            when(torneoRepository.findById(10L)).thenReturn(Optional.of(torneo));
            when(torneoSedeRepository.findByTorneoIdOrderByIdAsc(10L)).thenReturn(List.of(sede));

            TorneoSedeResponse respuesta = service.listar(10L).get(0);

            assertThat(respuesta.getLugarNombre()).isEqualTo("La Blanqueada");
            assertThat(respuesta.getMontoAcordado()).isNull();
            assertThat(respuesta.getPagos()).isNull();
        }
    }

    @Test
    @DisplayName("El club principal no se quita")
    void principalNoSeQuita() {
        TorneoSede sedePrincipal = TorneoSede.builder().id(6L).torneo(torneo).lugar(principal).build();
        when(torneoSedeRepository.findById(6L)).thenReturn(Optional.of(sedePrincipal));

        assertThatThrownBy(() -> service.quitar(10L, 6L))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessageContaining("principal");
    }
}
