package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.request.PagoJugadorRequest;
import com.padel.rankpadel.dto.response.CuentaInscripcionesResponse;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.Jugador;
import com.padel.rankpadel.entity.Pago;
import com.padel.rankpadel.entity.PagoInscripcion;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.SolicitudInscripcion;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.MedioPago;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.PagoInscripcionRepository;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.TorneoRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("CobroInscripcionService - quién pagó y quién debe")
class CobroInscripcionServiceTest {

    @Mock private PagoInscripcionRepository pagoInscripcionRepository;
    @Mock private ParejaRepository parejaRepository;
    @Mock private TorneoRepository torneoRepository;
    @Mock private MovimientoCajaService movimientoCajaService;
    @Mock private com.padel.rankpadel.config.Instalacion instalacion;

    @InjectMocks
    private CobroInscripcionService service;

    private Torneo torneo;
    private Jugador ana;
    private Jugador bea;
    private Pareja pareja;
    private final List<PagoInscripcion> guardados = new ArrayList<>();

    @BeforeEach
    void setUp() {
        torneo = Torneo.builder().id(1L).costoInscripcionJugador(new BigDecimal("20000")).build();
        ana = Jugador.builder().id(10L).nombre("Ana").apellido("Paz").build();
        bea = Jugador.builder().id(11L).nombre("Bea").apellido("Sol").build();
        pareja = Pareja.builder().id(5L).torneo(torneo).jugador1(ana).jugador2(bea)
                .categoria(Categoria.builder().id(3L).nombre("Damas C").build())
                // Se anotó cuando costaba 15.000: eso es lo que debe, aunque hoy cueste más.
                .costoInscripcionJugador(new BigDecimal("15000"))
                .build();
    }

    private PagoInscripcion pago(Jugador jugador, String monto) {
        return PagoInscripcion.builder().id((long) (guardados.size() + 100)).pareja(pareja).jugador(jugador)
                .monto(new BigDecimal(monto)).fecha(LocalDate.now()).medio(MedioPago.EFECTIVO).build();
    }

    private PagoJugadorRequest cobro(Long jugadorId, String monto) {
        PagoJugadorRequest request = new PagoJugadorRequest();
        request.setJugadorId(jugadorId);
        request.setFecha(LocalDate.now());
        request.setMonto(new BigDecimal(monto));
        request.setMedio(MedioPago.TRANSFERENCIA);
        return request;
    }

    @Test
    @DisplayName("La cuenta es por jugador, con el costo congelado de la pareja")
    void cuentaPorJugador() {
        guardados.add(pago(ana, "15000"));
        guardados.add(pago(bea, "5000"));
        when(torneoRepository.existsById(1L)).thenReturn(true);
        when(parejaRepository.findByTorneoId(1L)).thenReturn(List.of(pareja));
        when(pagoInscripcionRepository.findByParejaTorneoIdOrderByFechaAscIdAsc(1L)).thenReturn(guardados);

        CuentaInscripcionesResponse cuenta = service.cuenta(1L);

        assertThat(cuenta.getTotalEsperado()).isEqualByComparingTo("30000");
        assertThat(cuenta.getTotalCobrado()).isEqualByComparingTo("20000");
        assertThat(cuenta.getTotalPendiente()).isEqualByComparingTo("10000");
        assertThat(cuenta.getJugadoresConDeuda()).isEqualTo(1);
        assertThat(cuenta.getParejas().get(0).getJugadores())
                .extracting(j -> j.getNombre() + "=" + j.getSaldo().toPlainString())
                .containsExactly("Ana Paz=0", "Bea Sol=10000");
    }

    @Test
    @DisplayName("Un pago anulado no cuenta")
    void anuladoNoCuenta() {
        PagoInscripcion anulado = pago(ana, "15000");
        anulado.setAnuladoEn(LocalDateTime.now());
        guardados.add(anulado);
        when(torneoRepository.existsById(1L)).thenReturn(true);
        when(parejaRepository.findByTorneoId(1L)).thenReturn(List.of(pareja));
        when(pagoInscripcionRepository.findByParejaTorneoIdOrderByFechaAscIdAsc(1L)).thenReturn(guardados);

        assertThat(service.cuenta(1L).getTotalCobrado()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Acepta pagos parciales pero no cobrar de más")
    void parcialesYTope() {
        when(parejaRepository.findById(5L)).thenReturn(Optional.of(pareja));
        when(pagoInscripcionRepository.findByParejaId(5L)).thenReturn(List.of(pago(bea, "10000")));

        assertThatThrownBy(() -> service.registrar(1L, 5L, cobro(11L, "5001")))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessageContaining("Bea Sol debe $5.000");
        verify(pagoInscripcionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Solo se le cobra a un integrante de la pareja")
    void soloIntegrantes() {
        when(parejaRepository.findById(5L)).thenReturn(Optional.of(pareja));

        assertThatThrownBy(() -> service.registrar(1L, 5L, cobro(99L, "1000")))
                .isInstanceOf(EstadoInvalidoException.class);
    }

    @Test
    @DisplayName("La seña online se reparte entre los dos, sin perder un centavo, y una sola vez")
    @SuppressWarnings("unchecked")
    void seniaOnline() {
        Pago online = Pago.builder().id(77L).montoSenia(new BigDecimal("15000.01")).pagadoEn(LocalDateTime.now()).build();
        SolicitudInscripcion solicitud = SolicitudInscripcion.builder().pago(online).pareja(pareja).pagada(true).build();
        when(pagoInscripcionRepository.findByParejaId(5L)).thenReturn(List.of());

        service.registrarSeniaOnline(solicitud);

        ArgumentCaptor<List<PagoInscripcion>> captor = ArgumentCaptor.forClass(List.class);
        verify(pagoInscripcionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(p -> p.getJugador().getId() + ":" + p.getMonto().toPlainString())
                .containsExactly("10:7500.00", "11:7500.01");

        PagoInscripcion yaEsta = pago(ana, "7500");
        yaEsta.setPagoOnline(online);
        when(pagoInscripcionRepository.findByParejaId(5L)).thenReturn(List.of(yaEsta));
        service.registrarSeniaOnline(solicitud);
        verify(pagoInscripcionRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("No se borra una pareja que ya pagó")
    void parejaConPagos() {
        when(pagoInscripcionRepository.findByParejaId(5L)).thenReturn(List.of(pago(ana, "1000")));

        assertThatThrownBy(() -> service.exigirSinPagos(pareja))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessageContaining("retirala");
    }

    @Test
    @DisplayName("En un club, el cobro entra a la caja del día; al anularlo, sale")
    void clubVaALaCaja() {
        com.padel.rankpadel.entity.MovimientoCaja movimiento = com.padel.rankpadel.entity.MovimientoCaja.builder().id(70L).build();
        torneo.setNombre("Copa");
        when(parejaRepository.findById(5L)).thenReturn(Optional.of(pareja));
        when(pagoInscripcionRepository.findByParejaId(5L)).thenReturn(List.of());
        when(instalacion.esOrganizador()).thenReturn(false);
        when(movimientoCajaService.registrarDelSistema(org.mockito.ArgumentMatchers.eq(com.padel.rankpadel.enums.ConceptoMovimientoCaja.INSCRIPCION_TORNEO),
                org.mockito.ArgumentMatchers.eq("Inscripción Copa · Ana Paz"), any(), org.mockito.ArgumentMatchers.eq(MedioPago.TRANSFERENCIA)))
                .thenReturn(movimiento);
        when(torneoRepository.existsById(1L)).thenReturn(true);

        service.registrar(1L, 5L, cobro(10L, "5000"));

        ArgumentCaptor<PagoInscripcion> captor = ArgumentCaptor.forClass(PagoInscripcion.class);
        verify(pagoInscripcionRepository).save(captor.capture());
        assertThat(captor.getValue().getMovimientoCaja()).isSameAs(movimiento);

        PagoInscripcion guardado = captor.getValue();
        guardado.setId(80L);
        when(pagoInscripcionRepository.findById(80L)).thenReturn(Optional.of(guardado));
        service.anular(1L, 80L, "error");
        verify(movimientoCajaService).anularDelSistema(movimiento, "error");
    }

    @Test
    @DisplayName("Un organizador no tiene caja: el cobro queda solo en la cuenta del torneo")
    void organizadorSinCaja() {
        when(parejaRepository.findById(5L)).thenReturn(Optional.of(pareja));
        when(pagoInscripcionRepository.findByParejaId(5L)).thenReturn(List.of());
        when(instalacion.esOrganizador()).thenReturn(true);
        when(torneoRepository.existsById(1L)).thenReturn(true);

        service.registrar(1L, 5L, cobro(10L, "5000"));

        verify(movimientoCajaService, never()).registrarDelSistema(any(), any(), any(), any());
    }
}
