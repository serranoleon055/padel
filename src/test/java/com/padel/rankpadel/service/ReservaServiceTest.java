package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.request.SolicitudReservaRequest;
import com.padel.rankpadel.dto.response.ReservaResponse;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.Reserva;
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

    @InjectMocks
    private ReservaService reservaService;

    private Cancha cancha;

    @BeforeEach
    void setUp() {
        cancha = Cancha.builder().id(1L).nombre("Cancha 1").activo(true).build();
        when(disponibilidadCanchaService.inicioReal(eq(1L), any(LocalDate.class), any(LocalTime.class)))
                .thenAnswer(invocacion -> ((LocalDate) invocacion.getArgument(1))
                        .atTime((LocalTime) invocacion.getArgument(2)));
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
        when(disponibilidadCanchaService.duracionSlot(1L)).thenReturn(60);

        assertThrows(EstadoInvalidoException.class,
                () -> reservaService.solicitar(solicitud(LocalDate.now().minusDays(1), LocalTime.of(18, 0))));

        verify(reservaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Bloquea si el teléfono ya tiene demasiadas reservas pendientes (anti-abuso)")
    void solicitar_demasiadosPendientes_lanza() {
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(cancha));
        when(disponibilidadCanchaService.duracionSlot(1L)).thenReturn(60);
        when(reservaRepository.countByClienteTelefonoAndEstado("3851234567", EstadoReserva.PENDIENTE)).thenReturn(3L);

        assertThrows(EstadoInvalidoException.class,
                () -> reservaService.solicitar(solicitud(LocalDate.now().plusDays(1), LocalTime.of(18, 0))));

        verify(reservaRepository, never()).save(any());
    }

    @Test
    @DisplayName("No permite reservar un horario ya ocupado")
    void solicitar_slotOcupado_lanza() {
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(cancha));
        when(disponibilidadCanchaService.duracionSlot(1L)).thenReturn(60);
        when(reservaRepository.countByClienteTelefonoAndEstado(any(), eq(EstadoReserva.PENDIENTE))).thenReturn(0L);
        when(disponibilidadCanchaService.rangoLibre(eq(1L), any(), any(), any())).thenReturn(false);

        assertThrows(EstadoInvalidoException.class,
                () -> reservaService.solicitar(solicitud(LocalDate.now().plusDays(1), LocalTime.of(18, 0))));

        verify(reservaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Crea la reserva en estado PENDIENTE cuando el horario está libre")
    void solicitar_ok_creaPendiente() {
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(cancha));
        when(disponibilidadCanchaService.duracionSlot(1L)).thenReturn(60);
        when(reservaRepository.countByClienteTelefonoAndEstado(any(), eq(EstadoReserva.PENDIENTE))).thenReturn(0L);
        when(disponibilidadCanchaService.rangoLibre(eq(1L), any(), any(), any())).thenReturn(true);

        ReservaResponse respuesta = reservaService.solicitar(solicitud(LocalDate.now().plusDays(1), LocalTime.of(18, 0)));

        verify(reservaRepository).save(any(Reserva.class));
        assertThat(respuesta.getEstado()).isEqualTo("PENDIENTE");
        assertThat(respuesta.getHoraFin()).isEqualTo(LocalTime.of(19, 0));
    }
}
