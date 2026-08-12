package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
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

import com.padel.rankpadel.dto.response.DisponibilidadSedeResponse;
import com.padel.rankpadel.dto.response.OpcionDuracion;
import com.padel.rankpadel.dto.response.SlotDisponibilidad;
import com.padel.rankpadel.entity.BloqueoCancha;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.HorarioCancha;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.repository.BloqueoCanchaRepository;
import com.padel.rankpadel.repository.CanchaRepository;
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
    @Mock
    private CanchaRepository canchaRepository;
    @Mock
    private PromocionCanchaService promocionCanchaService;

    @InjectMocks
    private DisponibilidadCanchaService service;

    @Test
    @DisplayName("Una cancha sin horario cargado no arrastra la jornada a medianoche")
    void fechaDeJornadaActual_ignoraLasCanchasSinHorario() {
        Cancha conHorario = Cancha.builder().id(1L).nombre("Cancha 1").build();
        Cancha sinHorario = Cancha.builder().id(2L).nombre("Cancha 2").build();

        when(canchaRepository.findByActivoTrue()).thenReturn(List.of(conHorario, sinHorario));
        when(horarioCanchaRepository.findByCanchaIdAndActivoTrue(1L)).thenReturn(List.of(HorarioCancha.builder()
                .horaApertura(LocalTime.of(10, 0)).horaCierre(LocalTime.of(2, 0))
                .duracionesOfrecidas("60").anticipacionDias(14).activo(true).build()));
        when(horarioCanchaRepository.findByCanchaIdAndActivoTrue(2L)).thenReturn(List.of());

        // La cancha sin configurar devolvía las 00:00 como apertura y hacía que la
        // jornada fuera siempre la de hoy, incluso a las 3 de la mañana.
        LocalDate esperada = LocalTime.now().isBefore(LocalTime.of(10, 0))
                ? LocalDate.now().minusDays(1)
                : LocalDate.now();
        assertThat(service.fechaDeJornadaActual()).isEqualTo(esperada);
    }

    @Test
    @DisplayName("La grilla de la sede ordena por jornada: la madrugada va al final, no al principio")
    void disponibilidadSede_ordenaPorJornadaYNoPorReloj() {
        LocalDate fecha = LocalDate.now().plusDays(1);
        HorarioCancha horario = HorarioCancha.builder()
                .horaApertura(LocalTime.of(10, 0)).horaCierre(LocalTime.of(2, 0))
                .duracionesOfrecidas("60").anticipacionDias(14).diasActivos(null).activo(true).build();
        Cancha cancha = Cancha.builder().id(1L).nombre("Cancha 1")
                .precioPorHora(new java.math.BigDecimal("10000")).build();

        when(canchaRepository.findByLugarIdAndActivoTrue(7L)).thenReturn(List.of(cancha));
        when(horarioCanchaRepository.findByCanchaIdAndActivoTrue(1L)).thenReturn(List.of(horario));
        when(reservaRepository.findByCanchaIdAndFecha(1L, fecha)).thenReturn(List.of());
        when(bloqueoCanchaRepository.findByCanchaId(1L)).thenReturn(List.of());
        when(partidoRepository.findByCanchaIdAndFechaHoraProgramadaBetween(any(), any(), any())).thenReturn(List.of());

        List<LocalTime> horarios = service.disponibilidadSede(7L, fecha).getFranjas().stream()
                .map(DisponibilidadSedeResponse.FranjaSede::getHoraInicio)
                .toList();

        assertThat(horarios.get(0)).isEqualTo(LocalTime.of(10, 0));
        assertThat(horarios).endsWith(LocalTime.of(0, 0), LocalTime.of(1, 0));
    }

    @Test
    @DisplayName("Una reserva activa marca su horario como ocupado y deja libre el resto")
    void slots_reservaActivaOcupaElHorario() {
        LocalDate fecha = LocalDate.now().plusDays(1);
        HorarioCancha horario = HorarioCancha.builder()
                .horaApertura(LocalTime.of(18, 0)).horaCierre(LocalTime.of(20, 0))
                .duracionesOfrecidas("60").anticipacionDias(14).diasActivos(null).activo(true).build();
        Reserva reserva = Reserva.builder()
                .estado(EstadoReserva.CONFIRMADA)
                .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(19, 0)).duracionMin(60).build();

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
    @DisplayName("El desglose cuenta el mismo hueco con cada duración que vende el club")
    void turnosVendibles_desglosaPorDuracion() {
        // Abre 18 a 23 con un turno tomado de 19 a 20: quedan libres 18-19 y 20-23.
        // Eso es 4 turnos de una hora, o 1 solo de dos (20 a 22).
        LocalDate fecha = LocalDate.now().plusDays(1);
        HorarioCancha horario = HorarioCancha.builder()
                .horaApertura(LocalTime.of(18, 0)).horaCierre(LocalTime.of(23, 0))
                .duracionesOfrecidas("60,120").anticipacionDias(14).diasActivos(null).activo(true).build();
        Reserva reserva = Reserva.builder()
                .estado(EstadoReserva.CONFIRMADA)
                .horaInicio(LocalTime.of(19, 0)).horaFin(LocalTime.of(20, 0)).duracionMin(60).build();

        when(horarioCanchaRepository.findByCanchaIdAndActivoTrue(1L)).thenReturn(List.of(horario));
        when(reservaRepository.findByCanchaIdAndFecha(1L, fecha)).thenReturn(List.of(reserva));
        when(bloqueoCanchaRepository.findByCanchaId(1L)).thenReturn(List.of());
        when(partidoRepository.findByCanchaIdAndFechaHoraProgramadaBetween(any(), any(), any())).thenReturn(List.of());

        assertThat(service.turnosVendibles(1L, fecha))
                .containsExactly(entry(60, 4L), entry(120, 1L));
    }

    @Test
    @DisplayName("Cierre cerca de medianoche (23:59) genera slots finitos sin colgarse")
    void slots_cierreTardio_noBucleInfinito() {
        LocalDate fecha = LocalDate.now().plusDays(1);
        HorarioCancha horario = HorarioCancha.builder()
                .horaApertura(LocalTime.of(22, 0)).horaCierre(LocalTime.of(23, 59))
                .duracionesOfrecidas("60").anticipacionDias(14).diasActivos(null).activo(true).build();

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
                .duracionesOfrecidas("60").anticipacionDias(14).diasActivos(null).activo(true).build();

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
    @DisplayName("Los turnos arrancan en hora en punto aunque el club venda de 90 minutos")
    void slots_losInicioSonSiempreEnHoraEnPunto() {
        LocalDate fecha = LocalDate.now().plusDays(1);
        when(reservaRepository.findByCanchaIdAndFecha(1L, fecha)).thenReturn(List.of());
        when(bloqueoCanchaRepository.findByCanchaId(1L)).thenReturn(List.of());
        when(partidoRepository.findByCanchaIdAndFechaHoraProgramadaBetween(any(), any(), any())).thenReturn(List.of());

        when(horarioCanchaRepository.findByCanchaIdAndActivoTrue(1L)).thenReturn(List.of(horario("60,120")));
        assertThat(service.slots(1L, fecha)).extracting(SlotDisponibilidad::getHoraInicio)
                .containsExactly(LocalTime.of(18, 0), LocalTime.of(19, 0), LocalTime.of(20, 0), LocalTime.of(21, 0));

        // Agregar los 90 minutos suma una duración vendible, no horarios nuevos: en la
        // cancha se juega a las 20 o a las 21, nunca a las 20:30.
        when(horarioCanchaRepository.findByCanchaIdAndActivoTrue(1L)).thenReturn(List.of(horario("60,90,120")));
        assertThat(service.slots(1L, fecha)).extracting(SlotDisponibilidad::getHoraInicio)
                .containsExactly(LocalTime.of(18, 0), LocalTime.of(19, 0), LocalTime.of(20, 0), LocalTime.of(21, 0));
        assertThat(service.slots(1L, fecha).get(0).getOpciones())
                .extracting(OpcionDuracion::getMinutos).containsExactly(60, 90, 120);
    }

    @Test
    @DisplayName("No se ofrece una duración que no termina antes del cierre")
    void slots_duracionQueNoEntraAntesDelCierre_noSeOfrece() {
        LocalDate fecha = LocalDate.now().plusDays(1);
        when(horarioCanchaRepository.findByCanchaIdAndActivoTrue(1L)).thenReturn(List.of(horario("60,120")));
        when(reservaRepository.findByCanchaIdAndFecha(1L, fecha)).thenReturn(List.of());
        when(bloqueoCanchaRepository.findByCanchaId(1L)).thenReturn(List.of());
        when(partidoRepository.findByCanchaIdAndFechaHoraProgramadaBetween(any(), any(), any())).thenReturn(List.of());

        // Cierra a las 22: a las 21 solo entra el de una hora. Ofrecer el de dos sería
        // vender un turno que se corta a la mitad.
        SlotDisponibilidad ultimo = service.slots(1L, fecha).get(3);
        assertThat(ultimo.getHoraInicio()).isEqualTo(LocalTime.of(21, 0));
        assertThat(ultimo.getOpciones()).extracting(OpcionDuracion::getMinutos).containsExactly(60);
    }

    @Test
    @DisplayName("Un turno de 2 horas tapa también el horario del medio")
    void slots_turnoLargoOcupaTodoSuRango() {
        LocalDate fecha = LocalDate.now().plusDays(1);
        Reserva dosHoras = Reserva.builder()
                .estado(EstadoReserva.CONFIRMADA)
                .horaInicio(LocalTime.of(19, 0)).horaFin(LocalTime.of(21, 0)).duracionMin(120).build();

        when(horarioCanchaRepository.findByCanchaIdAndActivoTrue(1L)).thenReturn(List.of(horario("60,120")));
        when(reservaRepository.findByCanchaIdAndFecha(1L, fecha)).thenReturn(List.of(dosHoras));
        when(bloqueoCanchaRepository.findByCanchaId(1L)).thenReturn(List.of());
        when(partidoRepository.findByCanchaIdAndFechaHoraProgramadaBetween(any(), any(), any())).thenReturn(List.of());

        List<SlotDisponibilidad> slots = service.slots(1L, fecha);

        assertThat(slots.get(1).getHoraInicio()).isEqualTo(LocalTime.of(19, 0));
        assertThat(slots.get(1).isDisponible()).isFalse();
        assertThat(slots.get(2).getHoraInicio()).isEqualTo(LocalTime.of(20, 0));
        assertThat(slots.get(2).isDisponible()).isFalse();
        // A las 18 entra una hora (19 está tomada), pero no dos.
        assertThat(slots.get(0).getOpciones()).extracting(OpcionDuracion::getMinutos).containsExactly(60);
    }

    @Test
    @DisplayName("Un turno de 90 minutos toma tres bloques de media hora")
    void clavesSlot_turnoDe90_tomaTresBloques() {
        LocalDate fecha = LocalDate.of(2026, 8, 11);

        assertThat(service.clavesSlot(3L, fecha, LocalTime.of(19, 0), 90))
                .containsExactly("3|2026-08-11|19:00", "3|2026-08-11|19:30", "3|2026-08-11|20:00");
    }

    /** Cancha abierta de 18 a 22 con las duraciones indicadas. */
    private HorarioCancha horario(String duraciones) {
        return HorarioCancha.builder()
                .horaApertura(LocalTime.of(18, 0)).horaCierre(LocalTime.of(22, 0))
                .duracionesOfrecidas(duraciones).anticipacionDias(14).diasActivos(null).activo(true).build();
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

        assertThat(service.rangoLibre(1L, fecha, LocalTime.of(19, 0), 60)).isFalse();
        assertThat(service.rangoLibre(1L, fecha, LocalTime.of(22, 0), 60)).isTrue();
    }
}
