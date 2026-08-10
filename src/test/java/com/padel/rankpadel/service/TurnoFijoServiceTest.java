package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.padel.rankpadel.dto.response.GeneracionTurnosFijosResponse;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.entity.TurnoFijo;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.ReservaRepository;
import com.padel.rankpadel.repository.TurnoFijoRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("TurnoFijoService - generación de reservas de abonos")
class TurnoFijoServiceTest {

    @Mock
    private TurnoFijoRepository turnoFijoRepository;
    @Mock
    private CanchaRepository canchaRepository;
    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private ReservaService reservaService;
    @Mock
    private DisponibilidadCanchaService disponibilidadCanchaService;
    @Mock
    private NotificacionService notificacionService;

    @InjectMocks
    private TurnoFijoService turnoFijoService;

    private Cancha cancha;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(turnoFijoService, "semanasAdelante", 4);
        cancha = Cancha.builder().id(1L).nombre("Cancha 1").activo(true)
                .precioPorHora(new BigDecimal("20000")).build();
        lenient().when(disponibilidadCanchaService.duracionSlot(1L)).thenReturn(60);
    }

    /** Turno fijo los martes 20:00, vigente desde hoy, sin fecha de corte. */
    private TurnoFijo turnoMartes(int slots, BigDecimal precioPactado) {
        return TurnoFijo.builder()
                .id(5L)
                .cancha(cancha)
                .diaSemana(DayOfWeek.TUESDAY.getValue())
                .horaInicio(LocalTime.of(20, 0))
                .slots(slots)
                .clienteNombre("Grupo Gonzalo")
                .clienteTelefono("3856894061")
                .precioPactado(precioPactado)
                .vigenteDesde(LocalDate.now())
                .activo(true)
                .build();
    }

    @Test
    @DisplayName("Genera un turno por semana dentro de la ventana configurada")
    void genera_unTurnoPorSemana() {
        when(turnoFijoRepository.findActivosParaGenerar()).thenReturn(List.of(turnoMartes(1, null)));
        when(reservaRepository.findFechasGeneradas(eq(5L), any())).thenReturn(List.of());
        when(reservaService.crearParaTurnoFijo(any(), any(), any(), any()))
                .thenReturn(Optional.of(new Reserva()));

        GeneracionTurnosFijosResponse resultado = turnoFijoService.generarTodos();

        // 4 semanas de ventana: 4 o 5 martes según en qué día de la semana caiga hoy.
        assertThat(resultado.getGeneradas()).isBetween(4, 5);
        assertThat(resultado.getConflictos()).isEmpty();

        ArgumentCaptor<LocalDate> fechas = ArgumentCaptor.forClass(LocalDate.class);
        verify(reservaService, times(resultado.getGeneradas()))
                .crearParaTurnoFijo(any(), fechas.capture(), any(), any());
        assertThat(fechas.getAllValues()).allMatch(f -> f.getDayOfWeek() == DayOfWeek.TUESDAY);
    }

    @Test
    @DisplayName("Es idempotente: no vuelve a crear las fechas ya generadas")
    void genera_esIdempotente() {
        TurnoFijo turno = turnoMartes(1, null);
        LocalDate hoy = LocalDate.now();
        // Todas las fechas de la ventana ya existen como reserva.
        List<LocalDate> yaGeneradas = hoy.datesUntil(hoy.plusWeeks(4).plusDays(1))
                .filter(f -> f.getDayOfWeek() == DayOfWeek.TUESDAY)
                .toList();
        when(turnoFijoRepository.findActivosParaGenerar()).thenReturn(List.of(turno));
        when(reservaRepository.findFechasGeneradas(eq(5L), any())).thenReturn(yaGeneradas);

        GeneracionTurnosFijosResponse resultado = turnoFijoService.generarTodos();

        assertThat(resultado.getGeneradas()).isZero();
        verify(reservaService, never()).crearParaTurnoFijo(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Si el horario está ocupado, lo reporta como conflicto y avisa al club")
    void genera_horarioOcupado_reportaConflicto() {
        when(turnoFijoRepository.findActivosParaGenerar()).thenReturn(List.of(turnoMartes(1, null)));
        when(reservaRepository.findFechasGeneradas(eq(5L), any())).thenReturn(List.of());
        when(reservaService.crearParaTurnoFijo(any(), any(), any(), any())).thenReturn(Optional.empty());

        GeneracionTurnosFijosResponse resultado = turnoFijoService.generarTodos();

        assertThat(resultado.getGeneradas()).isZero();
        assertThat(resultado.getConflictos()).isNotEmpty();
        assertThat(resultado.getConflictos().get(0).getClienteNombre()).isEqualTo("Grupo Gonzalo");
        verify(notificacionService).avisarConflictosTurnosFijos(anyList());
    }

    @Test
    @DisplayName("Un turno de 2 horas genera los dos horarios y reparte el precio pactado")
    void genera_dosSlots_repartePrecio() {
        when(turnoFijoRepository.findActivosParaGenerar())
                .thenReturn(List.of(turnoMartes(2, new BigDecimal("50000"))));
        when(reservaRepository.findFechasGeneradas(eq(5L), any())).thenReturn(List.of());
        when(reservaService.crearParaTurnoFijo(any(), any(), any(), any()))
                .thenReturn(Optional.of(new Reserva()));

        turnoFijoService.generarTodos();

        ArgumentCaptor<LocalTime> horas = ArgumentCaptor.forClass(LocalTime.class);
        ArgumentCaptor<BigDecimal> precios = ArgumentCaptor.forClass(BigDecimal.class);
        verify(reservaService, times(8)).crearParaTurnoFijo(any(), any(), horas.capture(), precios.capture());

        assertThat(horas.getAllValues()).containsOnly(LocalTime.of(20, 0), LocalTime.of(21, 0));
        // El precio pactado es del turno completo: se guarda prorrateado por horario.
        assertThat(precios.getAllValues()).allMatch(p -> p.compareTo(new BigDecimal("25000.00")) == 0);
    }

    @Test
    @DisplayName("No genera nada más allá de la fecha de corte del abono")
    void genera_respetaVigenteHasta() {
        TurnoFijo turno = turnoMartes(1, null);
        turno.setVigenteHasta(LocalDate.now().plusDays(3));
        when(turnoFijoRepository.findActivosParaGenerar()).thenReturn(List.of(turno));
        when(reservaRepository.findFechasGeneradas(eq(5L), any())).thenReturn(List.of());
        lenient().when(reservaService.crearParaTurnoFijo(any(), any(), any(), any()))
                .thenReturn(Optional.of(new Reserva()));

        turnoFijoService.generarTodos();

        ArgumentCaptor<LocalDate> fechas = ArgumentCaptor.forClass(LocalDate.class);
        verify(reservaService, org.mockito.Mockito.atMost(1))
                .crearParaTurnoFijo(any(), fechas.capture(), any(), any());
        assertThat(fechas.getAllValues()).allMatch(f -> !f.isAfter(LocalDate.now().plusDays(3)));
    }
}
