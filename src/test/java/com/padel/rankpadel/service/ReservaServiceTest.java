package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.mapper.ReservaMapper;

import com.padel.rankpadel.dto.request.SolicitudReservaRequest;
import com.padel.rankpadel.dto.response.ReservaResponse;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.entity.ReservaSlot;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.ReservaRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservaService - solicitar turno")
class ReservaServiceTest {

    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private CanchaRepository canchaRepository;
    @Mock
    private DisponibilidadCanchaService disponibilidadCanchaService;
    @Mock
    private NotificacionService notificacionService;
    @Mock
    private ClienteService clienteService;
    /** El mapper es una función pura, no hay nada que simular. */
    @Spy
    private ReservaMapper reservaMapper = new ReservaMapper();

    @InjectMocks
    private ReservaService reservaService;

    private Cancha cancha;

    @BeforeEach
    void setUp() {
        cancha = Cancha.builder().id(1L).nombre("Cancha 1").activo(true)
                .precioPorHora(new BigDecimal("20000")).build();
        lenient().when(disponibilidadCanchaService.inicioReal(eq(1L), any(LocalDate.class), any(LocalTime.class)))
                .thenAnswer(invocacion -> ((LocalDate) invocacion.getArgument(1))
                        .atTime((LocalTime) invocacion.getArgument(2)));
        lenient().when(disponibilidadCanchaService.proximaApertura(eq(1L), any(LocalDateTime.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(1));
        lenient().when(disponibilidadCanchaService.precio(
                any(Cancha.class), any(LocalDate.class), any(LocalTime.class), anyInt()))
                .thenReturn(new BigDecimal("20000.00"));
    }

    private SolicitudReservaRequest solicitud(LocalDate fecha, LocalTime hora) {
        return SolicitudReservaRequest.builder()
                .canchaId(1L).fecha(fecha).horaInicio(hora)
                .clienteNombre("Juan").clienteTelefono("3851234567").build();
    }

    @Test
    @DisplayName("No permite reservar un horario que ya pasó")
    void solicitar_horarioPasado_lanza() {
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(cancha));
        when(disponibilidadCanchaService.duracionesOfrecidas(1L)).thenReturn(List.of(60, 120));

        assertThrows(EstadoInvalidoException.class,
                () -> reservaService.solicitar(solicitud(LocalDate.now().minusDays(1), LocalTime.of(18, 0))));

        verify(reservaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Bloquea si el teléfono ya tiene demasiadas reservas pendientes (anti-abuso)")
    void solicitar_demasiadosPendientes_lanza() {
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(cancha));
        when(reservaRepository.countByClienteTelefonoAndEstado("3851234567", EstadoReserva.PENDIENTE)).thenReturn(3L);

        assertThrows(EstadoInvalidoException.class,
                () -> reservaService.solicitar(solicitud(LocalDate.now().plusDays(1), LocalTime.of(18, 0))));

        verify(reservaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("No permite reservar un horario ya ocupado")
    void solicitar_slotOcupado_lanza() {
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(cancha));
        when(disponibilidadCanchaService.duracionesOfrecidas(1L)).thenReturn(List.of(60, 120));
        when(reservaRepository.countByClienteTelefonoAndEstado(any(), eq(EstadoReserva.PENDIENTE))).thenReturn(0L);
        when(disponibilidadCanchaService.rangoLibre(eq(1L), any(), any(), anyInt())).thenReturn(false);

        assertThrows(EstadoInvalidoException.class,
                () -> reservaService.solicitar(solicitud(LocalDate.now().plusDays(1), LocalTime.of(18, 0))));

        verify(reservaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Crea la reserva en estado PENDIENTE cuando el horario está libre")
    void solicitar_ok_creaPendiente() {
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(cancha));
        when(disponibilidadCanchaService.duracionesOfrecidas(1L)).thenReturn(List.of(60, 120));
        when(reservaRepository.countByClienteTelefonoAndEstado(any(), eq(EstadoReserva.PENDIENTE))).thenReturn(0L);
        when(disponibilidadCanchaService.rangoLibre(eq(1L), any(), any(), anyInt())).thenReturn(true);

        ReservaResponse respuesta = reservaService.solicitar(solicitud(LocalDate.now().plusDays(1), LocalTime.of(18, 0)));

        verify(reservaRepository).saveAndFlush(any(Reserva.class));
        assertThat(respuesta.getEstado()).isEqualTo("PENDIENTE");
        assertThat(respuesta.getHoraFin()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    @DisplayName("Congela el precio del turno en la reserva (el histórico no cambia si el club actualiza la tarifa)")
    void solicitar_congelaPrecioDelTurno() {
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(cancha));
        when(disponibilidadCanchaService.duracionesOfrecidas(1L)).thenReturn(List.of(60, 120));
        when(reservaRepository.countByClienteTelefonoAndEstado(any(), eq(EstadoReserva.PENDIENTE))).thenReturn(0L);
        when(disponibilidadCanchaService.rangoLibre(eq(1L), any(), any(), anyInt())).thenReturn(true);

        reservaService.solicitar(solicitud(LocalDate.now().plusDays(1), LocalTime.of(18, 0)));

        ArgumentCaptor<Reserva> guardada = ArgumentCaptor.forClass(Reserva.class);
        verify(reservaRepository).saveAndFlush(guardada.capture());
        assertThat(guardada.getValue().getPrecioAplicado()).isEqualByComparingTo("20000.00");
    }

    @Test
    @DisplayName("Una solicitud pedida con el club cerrado no vence antes de que el club abra")
    void solicitar_clubCerrado_noVenceAntesDeAbrir() {
        LocalDateTime apertura = LocalDateTime.now().plusHours(10);
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(cancha));
        when(disponibilidadCanchaService.duracionesOfrecidas(1L)).thenReturn(List.of(60, 120));
        when(reservaRepository.countByClienteTelefonoAndEstado(any(), eq(EstadoReserva.PENDIENTE))).thenReturn(0L);
        when(disponibilidadCanchaService.rangoLibre(eq(1L), any(), any(), anyInt())).thenReturn(true);
        when(disponibilidadCanchaService.proximaApertura(eq(1L), any(LocalDateTime.class))).thenReturn(apertura);

        reservaService.solicitar(solicitud(LocalDate.now().plusDays(3), LocalTime.of(18, 0)));

        ArgumentCaptor<Reserva> guardada = ArgumentCaptor.forClass(Reserva.class);
        verify(reservaRepository).saveAndFlush(guardada.capture());
        // Antes expiraba 90 minutos después de pedida: lo pedido de noche moría sin que el club lo viera.
        assertThat(guardada.getValue().getExpiraEn()).isAfter(apertura);
    }

    private Reserva reservaConfirmada(EstadoReserva estado, LocalDate fecha, LocalTime hora) {
        return Reserva.builder()
                .id(9L).cancha(cancha).estado(estado)
                .fecha(fecha).horaInicio(hora).horaFin(hora.plusHours(1)).duracionMin(60)
                .clienteNombre("Juan").clienteTelefono("3851234567").codigo("ABC123")
                .build();
    }

    @Test
    @DisplayName("Marca ausente un turno confirmado que ya pasó, sin liberar el horario")
    void noShow_turnoPasado_marca() {
        LocalDate ayer = LocalDate.now().minusDays(1);
        Reserva reserva = reservaConfirmada(EstadoReserva.CONFIRMADA, ayer, LocalTime.of(20, 0));
        reserva.getSlots().add(ReservaSlot.builder().reserva(reserva).claveSlot("1|" + ayer + "|20:00").build());
        when(reservaRepository.findById(9L)).thenReturn(Optional.of(reserva));

        reservaService.marcarNoShow(9L);

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.NO_SHOW);
        // La cancha estuvo bloqueada igual: el registro del slot no se borra.
        assertThat(reserva.getSlots()).isNotEmpty();
    }

    @Test
    @DisplayName("No permite marcar ausente un turno que todavía no empezó")
    void noShow_turnoFuturo_lanza() {
        Reserva reserva = reservaConfirmada(EstadoReserva.CONFIRMADA, LocalDate.now().plusDays(1), LocalTime.of(20, 0));
        when(reservaRepository.findById(9L)).thenReturn(Optional.of(reserva));

        assertThrows(EstadoInvalidoException.class, () -> reservaService.marcarNoShow(9L));
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
    }

    @Test
    @DisplayName("No permite marcar ausente un turno cancelado")
    void noShow_turnoCancelado_lanza() {
        Reserva reserva = reservaConfirmada(EstadoReserva.CANCELADA, LocalDate.now().minusDays(1), LocalTime.of(20, 0));
        when(reservaRepository.findById(9L)).thenReturn(Optional.of(reserva));

        assertThrows(EstadoInvalidoException.class, () -> reservaService.marcarNoShow(9L));
    }

    @Test
    @DisplayName("Un turno de 23 a 00 no se da por jugado antes de que termine")
    void finalizar_turnoQueCruzaMedianoche_noSeAdelanta() {
        // horaFin es 00:00, menor que cualquier hora del día: comparándola contra el reloj,
        // el turno aparecía como jugado desde que amanecía.
        LocalDate hoy = LocalDate.now();
        Reserva reserva = reservaConfirmada(EstadoReserva.CONFIRMADA, hoy, LocalTime.of(23, 0));
        reserva.setHoraFin(LocalTime.MIDNIGHT);
        when(reservaRepository.findByEstadoAndFechaLessThanEqual(EstadoReserva.CONFIRMADA, hoy))
                .thenReturn(List.of(reserva));
        when(disponibilidadCanchaService.horaApertura(1L)).thenReturn(LocalTime.of(18, 0));
        when(disponibilidadCanchaService.inicioEnSesion(any(), any(), any()))
                .thenReturn(LocalDateTime.now().plusMinutes(30));

        assertThat(reservaService.finalizarTurnosPasados()).isZero();
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        verify(reservaRepository, never()).save(any(Reserva.class));
    }

    @Test
    @DisplayName("Un turno que ya terminó se marca como jugado")
    void finalizar_turnoTerminado_seMarcaJugado() {
        LocalDate hoy = LocalDate.now();
        Reserva reserva = reservaConfirmada(EstadoReserva.CONFIRMADA, hoy, LocalTime.of(10, 0));
        when(reservaRepository.findByEstadoAndFechaLessThanEqual(EstadoReserva.CONFIRMADA, hoy))
                .thenReturn(List.of(reserva));
        when(disponibilidadCanchaService.horaApertura(1L)).thenReturn(LocalTime.of(8, 0));
        when(disponibilidadCanchaService.inicioEnSesion(any(), any(), any()))
                .thenReturn(LocalDateTime.now().minusHours(2));

        assertThat(reservaService.finalizarTurnosPasados()).isEqualTo(1);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.FINALIZADA);
    }

    @Test
    @DisplayName("Deshacer un ausente de un turno pasado lo devuelve a FINALIZADA")
    void noShow_deshacer_vuelveAFinalizada() {
        Reserva reserva = reservaConfirmada(EstadoReserva.NO_SHOW, LocalDate.now().minusDays(1), LocalTime.of(20, 0));
        when(reservaRepository.findById(9L)).thenReturn(Optional.of(reserva));

        reservaService.desmarcarNoShow(9L);

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.FINALIZADA);
    }

    @Test
    @DisplayName("La expiración nunca se estira más allá del comienzo del turno")
    void solicitar_expiracionNoPasaElInicioDelTurno() {
        LocalDate fecha = LocalDate.now().plusDays(1);
        LocalTime hora = LocalTime.of(18, 0);
        LocalDateTime inicioTurno = fecha.atTime(hora);
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(cancha));
        when(disponibilidadCanchaService.duracionesOfrecidas(1L)).thenReturn(List.of(60, 120));
        when(reservaRepository.countByClienteTelefonoAndEstado(any(), eq(EstadoReserva.PENDIENTE))).thenReturn(0L);
        when(disponibilidadCanchaService.rangoLibre(eq(1L), any(), any(), anyInt())).thenReturn(true);
        when(disponibilidadCanchaService.proximaApertura(eq(1L), any(LocalDateTime.class)))
                .thenReturn(inicioTurno.plusDays(2));

        reservaService.solicitar(solicitud(fecha, hora));

        ArgumentCaptor<Reserva> guardada = ArgumentCaptor.forClass(Reserva.class);
        verify(reservaRepository).saveAndFlush(guardada.capture());
        assertThat(guardada.getValue().getExpiraEn()).isEqualTo(inicioTurno);
    }
}
