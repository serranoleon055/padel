package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.padel.rankpadel.dto.request.TurnoFijoRequest;
import com.padel.rankpadel.dto.response.DisponibilidadAbonosResponse;
import com.padel.rankpadel.dto.response.GeneracionTurnosFijosResponse;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.HorarioCancha;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.entity.TurnoFijo;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.mapper.TurnoFijoMapper;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.ReservaRepository;
import com.padel.rankpadel.repository.TurnoFijoRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("TurnoFijoService - generación de reservas de abonos")
class TurnoFijoServiceTest {

    @Mock
    private ClienteService clienteService;
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
    /** Solo arma la respuesta; estos tests miran lo que se guarda, no lo que se devuelve. */
    @Mock
    private TurnoFijoMapper turnoFijoMapper;

    @InjectMocks
    private TurnoFijoService turnoFijoService;

    private Cancha cancha;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(turnoFijoService, "semanasAdelante", 4);
        cancha = Cancha.builder().id(1L).nombre("Cancha 1").activo(true)
                .precioPorHora(new BigDecimal("20000")).build();
        lenient().when(disponibilidadCanchaService.duracionesOfrecidas(1L)).thenReturn(List.of(60, 120));
    }

    /** Cuántos martes hay entre hoy y {@code hasta}, ambos inclusive. */
    private long martesHasta(LocalDate hasta) {
        return LocalDate.now().datesUntil(hasta.plusDays(1))
                .filter(fecha -> fecha.getDayOfWeek() == DayOfWeek.TUESDAY)
                .count();
    }

    /** Turno fijo los martes 20:00, vigente desde hoy, sin fecha de corte. */
    private TurnoFijo turnoMartes(int duracionMin, BigDecimal precioPactado) {
        return TurnoFijo.builder()
                .id(5L)
                .cancha(cancha)
                .diaSemana(DayOfWeek.TUESDAY.getValue())
                .horaInicio(LocalTime.of(20, 0))
                .duracionMin(duracionMin)
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
        when(turnoFijoRepository.findActivosParaGenerar()).thenReturn(List.of(turnoMartes(60, null)));
        when(reservaRepository.findFechasGeneradas(eq(5L), any())).thenReturn(List.of());
        when(reservaService.crearParaTurnoFijo(any(), any(), any(), any()))
                .thenReturn(Optional.of(new Reserva()));

        GeneracionTurnosFijosResponse resultado = turnoFijoService.generarTodos();

        assertThat(resultado.getGeneradas()).isEqualTo((int) martesHasta(LocalDate.now().plusWeeks(4)));
        assertThat(resultado.getConflictos()).isEmpty();

        ArgumentCaptor<LocalDate> fechas = ArgumentCaptor.forClass(LocalDate.class);
        verify(reservaService, times(resultado.getGeneradas()))
                .crearParaTurnoFijo(any(), fechas.capture(), any(), any());
        assertThat(fechas.getAllValues()).allMatch(f -> f.getDayOfWeek() == DayOfWeek.TUESDAY);
    }

    @Test
    @DisplayName("Es idempotente: no vuelve a crear las fechas ya generadas")
    void genera_esIdempotente() {
        TurnoFijo turno = turnoMartes(60, null);
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
        when(turnoFijoRepository.findActivosParaGenerar()).thenReturn(List.of(turnoMartes(60, null)));
        when(reservaRepository.findFechasGeneradas(eq(5L), any())).thenReturn(List.of());
        when(reservaService.crearParaTurnoFijo(any(), any(), any(), any())).thenReturn(Optional.empty());

        GeneracionTurnosFijosResponse resultado = turnoFijoService.generarTodos();

        assertThat(resultado.getGeneradas()).isZero();
        assertThat(resultado.getConflictos()).isNotEmpty();
        assertThat(resultado.getConflictos().get(0).getClienteNombre()).isEqualTo("Grupo Gonzalo");
        verify(notificacionService).avisarConflictosTurnosFijos(anyList());
    }

    @Test
    @DisplayName("Un abono de 2 horas genera UN turno por semana, no dos de una hora")
    void genera_dosHoras_unSoloTurnoConElPrecioEntero() {
        when(turnoFijoRepository.findActivosParaGenerar())
                .thenReturn(List.of(turnoMartes(120, new BigDecimal("50000"))));
        when(reservaRepository.findFechasGeneradas(eq(5L), any())).thenReturn(List.of());
        when(reservaService.crearParaTurnoFijo(any(), any(), any(), any()))
                .thenReturn(Optional.of(new Reserva()));

        GeneracionTurnosFijosResponse resultado = turnoFijoService.generarTodos();

        ArgumentCaptor<LocalTime> horas = ArgumentCaptor.forClass(LocalTime.class);
        ArgumentCaptor<BigDecimal> precios = ArgumentCaptor.forClass(BigDecimal.class);
        // La cantidad de martes en la ventana depende de en qué día caiga hoy: se cuenta
        // en vez de fijarla, para que el test no se rompa según el día que se corra.
        int martesEnLaVentana = (int) martesHasta(LocalDate.now().plusWeeks(4));
        verify(reservaService, times(martesEnLaVentana))
                .crearParaTurnoFijo(any(), any(), horas.capture(), precios.capture());

        // Antes eran dos reservas de una hora con el precio partido al medio, y el club
        // veía el mismo abono duplicado en la agenda y en las estadísticas.
        assertThat(resultado.getGeneradas()).isEqualTo(martesEnLaVentana);
        assertThat(horas.getAllValues()).containsOnly(LocalTime.of(20, 0));
        assertThat(precios.getAllValues()).allMatch(p -> p.compareTo(new BigDecimal("50000")) == 0);
    }

    @Nested
    @DisplayName("Alta de un abono: no puede pisar otro turno")
    class SuperposicionTests {

        private TurnoFijoRequest pedidoMartes20(int duracionMin) {
            return TurnoFijoRequest.builder()
                    .canchaId(1L)
                    .diaSemana(DayOfWeek.TUESDAY.getValue())
                    .horaInicio(LocalTime.of(20, 0))
                    .duracionMin(duracionMin)
                    .clienteNombre("Grupo Gonzalo")
                    .clienteTelefono("3856894061")
                    .vigenteDesde(LocalDate.now())
                    .build();
        }

        @BeforeEach
        void canchaDisponible() {
            lenient().when(canchaRepository.findById(1L)).thenReturn(Optional.of(cancha));
        }

        @Test
        @DisplayName("Se guarda si la cancha está libre en todas las fechas")
        void crear_sinChoques_guarda() {
            when(turnoFijoRepository.findActivosDelDia(eq(1L), eq(2), any())).thenReturn(List.of());
            when(disponibilidadCanchaService.rangoLibre(eq(1L), any(), any(), anyInt())).thenReturn(true);

            turnoFijoService.crear(pedidoMartes20(60));

            verify(turnoFijoRepository).save(any(TurnoFijo.class));
        }

        @Test
        @DisplayName("Rechaza el abono si choca con otro abono del mismo día y cancha")
        void crear_superponeOtroAbono_rechaza() {
            // El abono existente va de 19 a 21: pisa la primera hora del nuevo.
            TurnoFijo existente = turnoMartes(120, null);
            existente.setId(9L);
            existente.setHoraInicio(LocalTime.of(19, 0));
            existente.setClienteNombre("Los Pibes");
            when(turnoFijoRepository.findActivosDelDia(eq(1L), eq(2), any())).thenReturn(List.of(existente));

            assertThatThrownBy(() -> turnoFijoService.crear(pedidoMartes20(60)))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("Los Pibes");
            verify(turnoFijoRepository, never()).save(any(TurnoFijo.class));
        }

        @Test
        @DisplayName("Rechaza el abono si algún día ya tiene un turno confirmado en ese horario")
        void crear_horarioYaReservado_rechaza() {
            when(turnoFijoRepository.findActivosDelDia(eq(1L), eq(2), any())).thenReturn(List.of());
            when(disponibilidadCanchaService.rangoLibre(eq(1L), any(), any(), anyInt())).thenReturn(false);

            assertThatThrownBy(() -> turnoFijoService.crear(pedidoMartes20(60)))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("ya tienen la cancha tomada");
            verify(turnoFijoRepository, never()).save(any(TurnoFijo.class));
        }

        @Test
        @DisplayName("Dos abonos del mismo día no chocan si sus vigencias no se cruzan")
        void crear_vigenciasSeparadas_guarda() {
            TurnoFijo terminado = turnoMartes(60, null);
            terminado.setId(9L);
            terminado.setVigenteHasta(LocalDate.now().minusDays(1));
            when(turnoFijoRepository.findActivosDelDia(eq(1L), eq(2), any())).thenReturn(List.of(terminado));
            when(disponibilidadCanchaService.rangoLibre(eq(1L), any(), any(), anyInt())).thenReturn(true);

            turnoFijoService.crear(pedidoMartes20(60));

            verify(turnoFijoRepository).save(any(TurnoFijo.class));
        }
    }

    @Test
    @DisplayName("No genera nada más allá de la fecha de corte del abono")
    void genera_respetaVigenteHasta() {
        // El corte se ancla al primer martes en vez de a "hoy + 3 días": así el abono
        // genera exactamente una fecha corra el día que corra la suite. Con el plazo fijo,
        // de miércoles a viernes el martes quedaba fuera de la ventana y no generaba nada.
        LocalDate corte = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
        TurnoFijo turno = turnoMartes(60, null);
        turno.setVigenteHasta(corte);
        when(turnoFijoRepository.findActivosParaGenerar()).thenReturn(List.of(turno));
        when(reservaRepository.findFechasGeneradas(eq(5L), any())).thenReturn(List.of());
        when(reservaService.crearParaTurnoFijo(any(), any(), any(), any()))
                .thenReturn(Optional.of(new Reserva()));

        turnoFijoService.generarTodos();

        ArgumentCaptor<LocalDate> fechas = ArgumentCaptor.forClass(LocalDate.class);
        verify(reservaService, times(1))
                .crearParaTurnoFijo(any(), fechas.capture(), any(), any());
        assertThat(fechas.getAllValues()).allMatch(f -> !f.isAfter(corte));
    }

    @Nested
    @DisplayName("Grilla de turnos fijos disponibles")
    class DisponibilidadSemanal {

        /** Club que abre a las 10 y cierra a las 2 de la madrugada, todos los días. */
        private void clubDe10A02() {
            HorarioCancha horario = HorarioCancha.builder()
                    .cancha(cancha).horaApertura(LocalTime.of(10, 0))
                    .horaCierre(LocalTime.of(2, 0)).diasActivos("1,2,3,4,5,6,7").activo(true).build();
            when(canchaRepository.findByLugarIdAndActivoTrue(7L)).thenReturn(List.of(cancha));
            when(disponibilidadCanchaService.horarioDe(1L)).thenReturn(horario);
            when(disponibilidadCanchaService.atiendeElDia(eq(horario), anyInt())).thenReturn(true);
        }

        private DisponibilidadAbonosResponse.CanchaDelDia martesDe(DisponibilidadAbonosResponse respuesta) {
            return respuesta.dias().get(DayOfWeek.TUESDAY.getValue() - 1).canchas().get(0);
        }

        @Test
        @DisplayName("Sin abonos, toda la jornada está disponible")
        void sinAbonos_jornadaCompleta() {
            clubDe10A02();
            when(turnoFijoRepository.buscar(7L, null)).thenReturn(List.of());

            DisponibilidadAbonosResponse respuesta = turnoFijoService.disponibilidadSemanal(7L);

            assertThat(respuesta.dias()).hasSize(7);
            assertThat(martesDe(respuesta).cerrada()).isFalse();
            assertThat(martesDe(respuesta).franjas())
                    .containsExactly(new DisponibilidadAbonosResponse.Franja(LocalTime.of(10, 0), LocalTime.of(2, 0)));
        }

        @Test
        @DisplayName("Un abono parte la jornada en dos franjas y no aparece en los otros días")
        void conAbono_partelaJornada() {
            clubDe10A02();
            when(turnoFijoRepository.buscar(7L, null)).thenReturn(List.of(turnoMartes(120, null)));

            DisponibilidadAbonosResponse respuesta = turnoFijoService.disponibilidadSemanal(7L);

            assertThat(martesDe(respuesta).franjas()).containsExactly(
                    new DisponibilidadAbonosResponse.Franja(LocalTime.of(10, 0), LocalTime.of(20, 0)),
                    new DisponibilidadAbonosResponse.Franja(LocalTime.of(22, 0), LocalTime.of(2, 0)));
            DisponibilidadAbonosResponse.CanchaDelDia miercoles = respuesta.dias().get(2).canchas().get(0);
            assertThat(miercoles.franjas())
                    .containsExactly(new DisponibilidadAbonosResponse.Franja(LocalTime.of(10, 0), LocalTime.of(2, 0)));
        }

        /**
         * El caso que motiva medir en minutos desde la apertura: un abono de las 23 es el
         * ÚLTIMO de la noche, no el primero. Ordenando por reloj quedaba antes que el de
         * las 15 y los huecos salían dados vuelta.
         */
        @Test
        @DisplayName("Un abono de la medianoche se ubica al final de la jornada, no al principio")
        void abonoDeMedianoche_vaAlFinal() {
            clubDe10A02();
            TurnoFijo tarde = turnoMartes(120, null);
            TurnoFijo nocturno = turnoMartes(120, null);
            nocturno.setId(6L);
            nocturno.setHoraInicio(LocalTime.of(0, 0));
            tarde.setHoraInicio(LocalTime.of(15, 0));
            when(turnoFijoRepository.buscar(7L, null)).thenReturn(List.of(nocturno, tarde));

            DisponibilidadAbonosResponse respuesta = turnoFijoService.disponibilidadSemanal(7L);

            assertThat(martesDe(respuesta).franjas()).containsExactly(
                    new DisponibilidadAbonosResponse.Franja(LocalTime.of(10, 0), LocalTime.of(15, 0)),
                    new DisponibilidadAbonosResponse.Franja(LocalTime.of(17, 0), LocalTime.of(0, 0)));
        }

        @Test
        @DisplayName("Un abono con fecha de corte pasada ya no ocupa")
        void abonoVencido_noOcupa() {
            clubDe10A02();
            TurnoFijo vencido = turnoMartes(120, null);
            vencido.setVigenteHasta(LocalDate.now().minusDays(1));
            when(turnoFijoRepository.buscar(7L, null)).thenReturn(List.of(vencido));

            DisponibilidadAbonosResponse respuesta = turnoFijoService.disponibilidadSemanal(7L);

            assertThat(martesDe(respuesta).franjas())
                    .containsExactly(new DisponibilidadAbonosResponse.Franja(LocalTime.of(10, 0), LocalTime.of(2, 0)));
        }

        @Test
        @DisplayName("El día que el club no atiende sale como cerrado, no como libre")
        void diaSinAtencion_saleCerrado() {
            HorarioCancha horario = HorarioCancha.builder()
                    .cancha(cancha).horaApertura(LocalTime.of(10, 0))
                    .horaCierre(LocalTime.of(2, 0)).diasActivos("1,2,3,4,5,6").activo(true).build();
            when(canchaRepository.findByLugarIdAndActivoTrue(7L)).thenReturn(List.of(cancha));
            when(disponibilidadCanchaService.horarioDe(1L)).thenReturn(horario);
            when(disponibilidadCanchaService.atiendeElDia(eq(horario), anyInt()))
                    .thenAnswer(invocacion -> invocacion.getArgument(1, Integer.class) != 7);
            when(turnoFijoRepository.buscar(7L, null)).thenReturn(List.of());

            DisponibilidadAbonosResponse respuesta = turnoFijoService.disponibilidadSemanal(7L);

            DisponibilidadAbonosResponse.CanchaDelDia domingo = respuesta.dias().get(6).canchas().get(0);
            assertThat(domingo.cerrada()).isTrue();
            assertThat(domingo.franjas()).isEmpty();
        }

        /** Una cancha sin horario cargado no se puede vender: no es que esté todo libre. */
        @Test
        @DisplayName("Una cancha sin horario cargado sale cerrada todos los días")
        void sinHorarioCargado_saleCerrada() {
            when(canchaRepository.findByLugarIdAndActivoTrue(7L)).thenReturn(List.of(cancha));
            when(disponibilidadCanchaService.horarioDe(1L)).thenReturn(null);
            when(turnoFijoRepository.buscar(7L, null)).thenReturn(List.of());

            DisponibilidadAbonosResponse respuesta = turnoFijoService.disponibilidadSemanal(7L);

            assertThat(respuesta.dias()).allSatisfy(dia ->
                    assertThat(dia.canchas().get(0).cerrada()).isTrue());
        }
    }
}
