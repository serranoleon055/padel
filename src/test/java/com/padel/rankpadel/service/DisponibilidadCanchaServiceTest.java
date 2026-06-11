package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.response.SlotDisponibilidad;
import com.padel.rankpadel.entity.BloqueoCancha;
import com.padel.rankpadel.entity.HorarioCancha;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.repository.BloqueoCanchaRepository;
import com.padel.rankpadel.repository.HorarioCanchaRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.ReservaRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("DisponibilidadCanchaService - solapamientos")
class DisponibilidadCanchaServiceTest {

    @Mock
    private HorarioCanchaRepository horarioCanchaRepository;
    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private BloqueoCanchaRepository bloqueoCanchaRepository;
    @Mock
    private PartidoRepository partidoRepository;

    @InjectMocks
    private DisponibilidadCanchaService service;

    @Test
    @DisplayName("Una reserva activa marca su horario como ocupado y deja libre el resto")
    void slots_reservaActivaOcupaElHorario() {
        LocalDate fecha = LocalDate.now().plusDays(1);
        HorarioCancha horario = HorarioCancha.builder()
                .horaApertura(LocalTime.of(18, 0)).horaCierre(LocalTime.of(20, 0))
                .duracionSlotMin(60).anticipacionDias(14).diasActivos(null).activo(true).build();
        Reserva reserva = Reserva.builder()
                .estado(EstadoReserva.CONFIRMADA)
                .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(19, 0)).build();

        when(horarioCanchaRepository.findByCanchaIdAndActivoTrue(1L)).thenReturn(List.of(horario));
        when(reservaRepository.findByCanchaIdAndFecha(1L, fecha)).thenReturn(List.of(reserva));
        when(bloqueoCanchaRepository.findByCanchaId(1L)).thenReturn(List.of());
        when(partidoRepository.findByCanchaIdAndFechaHoraProgramadaBetween(any(), any(), any())).thenReturn(List.of());

        List<SlotDisponibilidad> slots = service.slots(1L, fecha);

        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).getHoraInicio()).isEqualTo(LocalTime.of(18, 0));
        assertThat(slots.get(0).isDisponible()).isFalse();
        assertThat(slots.get(1).getHoraInicio()).isEqualTo(LocalTime.of(19, 0));
        assertThat(slots.get(1).isDisponible()).isTrue();
    }

    @Test
    @DisplayName("Cierre cerca de medianoche (23:59) genera slots finitos sin colgarse")
    void slots_cierreTardio_noBucleInfinito() {
        LocalDate fecha = LocalDate.now().plusDays(1);
        HorarioCancha horario = HorarioCancha.builder()
                .horaApertura(LocalTime.of(22, 0)).horaCierre(LocalTime.of(23, 59))
                .duracionSlotMin(60).anticipacionDias(14).diasActivos(null).activo(true).build();

        when(horarioCanchaRepository.findByCanchaIdAndActivoTrue(1L)).thenReturn(List.of(horario));
        when(reservaRepository.findByCanchaIdAndFecha(1L, fecha)).thenReturn(List.of());
        when(bloqueoCanchaRepository.findByCanchaId(1L)).thenReturn(List.of());
        when(partidoRepository.findByCanchaIdAndFechaHoraProgramadaBetween(any(), any(), any())).thenReturn(List.of());

        List<SlotDisponibilidad> slots = service.slots(1L, fecha);

        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).getHoraInicio()).isEqualTo(LocalTime.of(22, 0));
        assertThat(slots.get(1).getHoraInicio()).isEqualTo(LocalTime.of(23, 0));
        assertThat(slots.get(1).getHoraFin()).isEqualTo(LocalTime.of(0, 0));
    }

    @Test
    @DisplayName("Horario que cruza medianoche (10:00 a 02:00) ofrece slots de la madrugada")
    void slots_horarioNocturno_incluyeMadrugada() {
        LocalDate fecha = LocalDate.now().plusDays(1);
        HorarioCancha horario = HorarioCancha.builder()
                .horaApertura(LocalTime.of(10, 0)).horaCierre(LocalTime.of(2, 0))
                .duracionSlotMin(60).anticipacionDias(14).diasActivos(null).activo(true).build();

        when(horarioCanchaRepository.findByCanchaIdAndActivoTrue(1L)).thenReturn(List.of(horario));
        when(reservaRepository.findByCanchaIdAndFecha(1L, fecha)).thenReturn(List.of());
        when(bloqueoCanchaRepository.findByCanchaId(1L)).thenReturn(List.of());
        when(partidoRepository.findByCanchaIdAndFechaHoraProgramadaBetween(any(), any(), any())).thenReturn(List.of());

        List<SlotDisponibilidad> slots = service.slots(1L, fecha);

        assertThat(slots).hasSize(16);
        assertThat(slots.get(0).getHoraInicio()).isEqualTo(LocalTime.of(10, 0));
        assertThat(slots).anyMatch(s -> s.getHoraInicio().equals(LocalTime.of(0, 0)));
        assertThat(slots).anyMatch(s -> s.getHoraInicio().equals(LocalTime.of(1, 0)));
    }

    @Test
    @DisplayName("Un bloqueo de cancha hace que el rango se considere ocupado")
    void rangoLibre_falseSiSeSolapaConBloqueo() {
        LocalDate fecha = LocalDate.now().plusDays(1);
        BloqueoCancha bloqueo = BloqueoCancha.builder()
                .inicio(fecha.atTime(18, 0)).fin(fecha.atTime(22, 0)).build();

        when(reservaRepository.findByCanchaIdAndFecha(1L, fecha)).thenReturn(List.of());
        when(bloqueoCanchaRepository.findByCanchaId(1L)).thenReturn(List.of(bloqueo));
        when(partidoRepository.findByCanchaIdAndFechaHoraProgramadaBetween(any(), any(), any())).thenReturn(List.of());

        assertThat(service.rangoLibre(1L, fecha, LocalTime.of(19, 0), LocalTime.of(20, 0))).isFalse();
        assertThat(service.rangoLibre(1L, fecha, LocalTime.of(22, 0), LocalTime.of(23, 0))).isTrue();
    }
}
