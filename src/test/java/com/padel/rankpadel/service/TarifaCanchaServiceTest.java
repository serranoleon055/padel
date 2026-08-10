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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.request.TarifaCanchaRequest;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.TarifaCancha;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.TarifaCanchaRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("TarifaCanchaService - precio por franja horaria")
class TarifaCanchaServiceTest {

    @Mock
    private TarifaCanchaRepository tarifaCanchaRepository;
    @Mock
    private CanchaRepository canchaRepository;

    @InjectMocks
    private TarifaCanchaService tarifaCanchaService;

    private final Cancha cancha = Cancha.builder().id(1L).nombre("Cancha 1")
            .precioPorHora(new BigDecimal("20000")).activo(true).build();

    // 2026-08-14 es viernes; 2026-08-11, martes.
    private static final LocalDate VIERNES = LocalDate.of(2026, 8, 14);
    private static final LocalDate MARTES = LocalDate.of(2026, 8, 11);
    private static final LocalDate SABADO = LocalDate.of(2026, 8, 15);

    private TarifaCancha tarifa(String nombre, String dias, String desde, String hasta, String precio) {
        return TarifaCancha.builder()
                .id(1L).cancha(cancha).nombre(nombre).diasSemana(dias)
                .horaDesde(LocalTime.parse(desde)).horaHasta(LocalTime.parse(hasta))
                .precioPorHora(new BigDecimal(precio)).activo(true)
                .build();
    }

    @Nested
    @DisplayName("Resolución del precio")
    class Resolucion {

        @Test
        @DisplayName("Aplica la franja que cubre el día y la hora")
        void aplicaLaFranjaQueCubre() {
            when(tarifaCanchaRepository.findByCanchaIdAndActivoTrue(1L)).thenReturn(List.of(
                    tarifa("Valle", "1,2,3,4,5", "08:00", "18:00", "14000"),
                    tarifa("Pico", "1,2,3,4,5", "18:00", "23:59", "26000")));

            assertThat(tarifaCanchaService.precioPorHora(1L, MARTES, LocalTime.of(15, 0)))
                    .isEqualByComparingTo("14000");
            assertThat(tarifaCanchaService.precioPorHora(1L, MARTES, LocalTime.of(21, 0)))
                    .isEqualByComparingTo("26000");
        }

        @Test
        @DisplayName("Devuelve null si ninguna franja cubre el horario, para que mande la tarifa de la cancha")
        void sinFranjaQueCubra_devuelveNull() {
            when(tarifaCanchaRepository.findByCanchaIdAndActivoTrue(1L))
                    .thenReturn(List.of(tarifa("Pico", "1,2,3,4,5", "18:00", "23:00", "26000")));

            assertThat(tarifaCanchaService.precioPorHora(1L, MARTES, LocalTime.of(10, 0))).isNull();
        }

        @Test
        @DisplayName("No aplica una franja de otro día")
        void otroDia_noAplica() {
            when(tarifaCanchaRepository.findByCanchaIdAndActivoTrue(1L))
                    .thenReturn(List.of(tarifa("Finde", "6,7", "10:00", "23:00", "30000")));

            assertThat(tarifaCanchaService.precioPorHora(1L, MARTES, LocalTime.of(15, 0))).isNull();
            assertThat(tarifaCanchaService.precioPorHora(1L, SABADO, LocalTime.of(15, 0)))
                    .isEqualByComparingTo("30000");
        }

        @Test
        @DisplayName("Una franja que cruza medianoche cubre las horas de la madrugada")
        void cruzaMedianoche() {
            // El turno de la 1 AM del sábado se agenda como parte de la noche del viernes.
            when(tarifaCanchaRepository.findByCanchaIdAndActivoTrue(1L))
                    .thenReturn(List.of(tarifa("Nocturno", "5,6", "20:00", "02:00", "32000")));

            assertThat(tarifaCanchaService.precioPorHora(1L, VIERNES, LocalTime.of(22, 0)))
                    .isEqualByComparingTo("32000");
            assertThat(tarifaCanchaService.precioPorHora(1L, VIERNES, LocalTime.of(1, 0)))
                    .isEqualByComparingTo("32000");
            assertThat(tarifaCanchaService.precioPorHora(1L, VIERNES, LocalTime.of(19, 0))).isNull();
        }

        @Test
        @DisplayName("Una franja desactivada no se aplica")
        void desactivada_noAplica() {
            TarifaCancha inactiva = tarifa("Vieja", "1,2,3,4,5", "08:00", "23:00", "9000");
            inactiva.setActivo(false);
            when(tarifaCanchaRepository.findByCanchaIdAndActivoTrue(1L)).thenReturn(List.of(inactiva));

            assertThat(tarifaCanchaService.precioPorHora(1L, MARTES, LocalTime.of(15, 0))).isNull();
        }
    }

    @Nested
    @DisplayName("Validación al cargar")
    class Validacion {

        @BeforeEach
        void setUp() {
            lenient().when(canchaRepository.findById(1L)).thenReturn(Optional.of(cancha));
        }

        private TarifaCanchaRequest pedido(String dias, String desde, String hasta) {
            TarifaCanchaRequest request = new TarifaCanchaRequest();
            request.setCanchaId(1L);
            request.setNombre("Nueva");
            request.setDiasSemana(dias);
            request.setHoraDesde(LocalTime.parse(desde));
            request.setHoraHasta(LocalTime.parse(hasta));
            request.setPrecioPorHora(new BigDecimal("20000"));
            return request;
        }

        @Test
        @DisplayName("Rechaza una franja que se superpone con otra del mismo día")
        void rechazaSuperposicion() {
            when(tarifaCanchaRepository.findByCanchaIdAndActivoTrue(1L))
                    .thenReturn(List.of(tarifa("Pico", "1,2,3", "18:00", "23:00", "26000")));

            // Dos franjas superpuestas dejarían el precio librado al orden de la consulta.
            assertThatThrownBy(() -> tarifaCanchaService.crear(pedido("3,4", "20:00", "22:00")))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("Pico");
            verify(tarifaCanchaRepository, never()).save(any());
        }

        @Test
        @DisplayName("Permite franjas contiguas que no se pisan")
        void permiteContiguas() {
            when(tarifaCanchaRepository.findByCanchaIdAndActivoTrue(1L))
                    .thenReturn(List.of(tarifa("Valle", "1,2,3", "08:00", "18:00", "14000")));

            tarifaCanchaService.crear(pedido("1,2,3", "18:00", "23:00"));

            verify(tarifaCanchaRepository).save(any(TarifaCancha.class));
        }

        @Test
        @DisplayName("Permite el mismo horario en días distintos")
        void permiteMismoHorarioOtroDia() {
            when(tarifaCanchaRepository.findByCanchaIdAndActivoTrue(1L))
                    .thenReturn(List.of(tarifa("Semana", "1,2,3,4,5", "18:00", "23:00", "26000")));

            tarifaCanchaService.crear(pedido("6,7", "18:00", "23:00"));

            verify(tarifaCanchaRepository).save(any(TarifaCancha.class));
        }

        @Test
        @DisplayName("Detecta la superposición de una franja que cruza medianoche")
        void detectaSuperposicionCruzandoMedianoche() {
            when(tarifaCanchaRepository.findByCanchaIdAndActivoTrue(1L))
                    .thenReturn(List.of(tarifa("Nocturno", "5,6", "20:00", "02:00", "32000")));

            assertThatThrownBy(() -> tarifaCanchaService.crear(pedido("5", "00:00", "01:00")))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("Nocturno");
        }

        @Test
        @DisplayName("Rechaza una franja sin duración")
        void rechazaFranjaVacia() {
            assertThatThrownBy(() -> tarifaCanchaService.crear(pedido("1", "20:00", "20:00")))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("duración");
        }
    }
}
