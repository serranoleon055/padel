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

import com.padel.rankpadel.dto.request.PromocionCanchaRequest;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.PromocionCancha;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.PromocionCanchaRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromocionCanchaService - precios especiales por día y horario")
class PromocionCanchaServiceTest {

    @Mock
    private PromocionCanchaRepository promocionCanchaRepository;
    @Mock
    private CanchaRepository canchaRepository;

    @InjectMocks
    private PromocionCanchaService promocionCanchaService;

    private final Cancha cancha = Cancha.builder().id(1L).nombre("Cancha 1")
            .precioPorHora(new BigDecimal("20000")).activo(true).build();

    // 2026-08-14 es viernes; 2026-08-11, martes.
    private static final LocalDate VIERNES = LocalDate.of(2026, 8, 14);
    private static final LocalDate MARTES = LocalDate.of(2026, 8, 11);
    private static final LocalDate SABADO = LocalDate.of(2026, 8, 15);

    private PromocionCancha promo(String nombre, String dias, String desde, String hasta, String precio) {
        return PromocionCancha.builder()
                .id(1L).cancha(cancha).nombre(nombre).diasSemana(dias)
                .horaDesde(LocalTime.parse(desde)).horaHasta(LocalTime.parse(hasta))
                .precioPorHora(new BigDecimal(precio)).activo(true)
                .build();
    }

    @Nested
    @DisplayName("Vigencia de la promoción")
    class Vigencia {

        @Test
        @DisplayName("Una promoción que todavía no arrancó no se aplica")
        void antesDeArrancar_noSeAplica() {
            PromocionCancha futura = promo("Promo primavera", "5", "08:00", "23:59", "12000");
            futura.setVigenteDesde(VIERNES.plusDays(7));
            when(promocionCanchaRepository.findByCanchaIdAndActivoTrue(1L)).thenReturn(List.of(futura));

            assertThat(promocionCanchaService.precioPorHora(1L, VIERNES, LocalTime.of(20, 0))).isNull();
        }

        @Test
        @DisplayName("Una promoción vencida deja de aplicarse sola")
        void despuesDelCorte_noSeAplica() {
            PromocionCancha vencida = promo("Promo julio", "5", "08:00", "23:59", "12000");
            vencida.setVigenteHasta(VIERNES.minusDays(1));
            when(promocionCanchaRepository.findByCanchaIdAndActivoTrue(1L)).thenReturn(List.of(vencida));

            assertThat(promocionCanchaService.precioPorHora(1L, VIERNES, LocalTime.of(20, 0))).isNull();
        }

        @Test
        @DisplayName("Dentro de la vigencia se aplica el precio de la promoción")
        void dentroDeLaVigencia_seAplica() {
            PromocionCancha vigente = promo("Promo mediodía", "5", "12:00", "17:00", "12000");
            vigente.setVigenteDesde(VIERNES.minusDays(3));
            vigente.setVigenteHasta(VIERNES.plusDays(3));
            when(promocionCanchaRepository.findByCanchaIdAndActivoTrue(1L)).thenReturn(List.of(vigente));

            assertThat(promocionCanchaService.precioPorHora(1L, VIERNES, LocalTime.of(13, 0)))
                    .isEqualByComparingTo("12000");
        }

        @Test
        @DisplayName("Dos promociones del mismo horario conviven si una empieza cuando la otra termina")
        void vigenciasSeparadas_seAceptan() {
            PromocionCancha terminada = promo("Promo julio", "5", "12:00", "17:00", "12000");
            terminada.setId(9L);
            terminada.setVigenteHasta(VIERNES.minusDays(1));
            when(promocionCanchaRepository.findByCanchaIdAndActivoTrue(1L)).thenReturn(List.of(terminada));
            when(canchaRepository.findById(1L)).thenReturn(Optional.of(cancha));

            PromocionCanchaRequest nueva = new PromocionCanchaRequest();
            nueva.setCanchaId(1L);
            nueva.setNombre("Promo agosto");
            nueva.setDiasSemana("5");
            nueva.setHoraDesde(LocalTime.of(12, 0));
            nueva.setHoraHasta(LocalTime.of(17, 0));
            nueva.setPrecioPorHora(new BigDecimal("14000"));
            nueva.setVigenteDesde(VIERNES);

            promocionCanchaService.crear(nueva);

            verify(promocionCanchaRepository).save(any(PromocionCancha.class));
        }
    }

    @Nested
    @DisplayName("Resolución del precio")
    class Resolucion {

        @Test
        @DisplayName("Aplica la promoción que cubre el día y la hora")
        void aplicaLaFranjaQueCubre() {
            when(promocionCanchaRepository.findByCanchaIdAndActivoTrue(1L)).thenReturn(List.of(
                    promo("Valle", "1,2,3,4,5", "08:00", "18:00", "14000"),
                    promo("Pico", "1,2,3,4,5", "18:00", "23:59", "26000")));

            assertThat(promocionCanchaService.precioPorHora(1L, MARTES, LocalTime.of(15, 0)))
                    .isEqualByComparingTo("14000");
            assertThat(promocionCanchaService.precioPorHora(1L, MARTES, LocalTime.of(21, 0)))
                    .isEqualByComparingTo("26000");
        }

        @Test
        @DisplayName("Devuelve null si ninguna promoción cubre el horario, para que mande la tarifa de la cancha")
        void sinPromocionQueCubra_devuelveNull() {
            when(promocionCanchaRepository.findByCanchaIdAndActivoTrue(1L))
                    .thenReturn(List.of(promo("Pico", "1,2,3,4,5", "18:00", "23:00", "26000")));

            assertThat(promocionCanchaService.precioPorHora(1L, MARTES, LocalTime.of(10, 0))).isNull();
        }

        @Test
        @DisplayName("No aplica una promoción de otro día")
        void otroDia_noAplica() {
            when(promocionCanchaRepository.findByCanchaIdAndActivoTrue(1L))
                    .thenReturn(List.of(promo("Finde", "6,7", "10:00", "23:00", "30000")));

            assertThat(promocionCanchaService.precioPorHora(1L, MARTES, LocalTime.of(15, 0))).isNull();
            assertThat(promocionCanchaService.precioPorHora(1L, SABADO, LocalTime.of(15, 0)))
                    .isEqualByComparingTo("30000");
        }

        @Test
        @DisplayName("Una promoción que cruza medianoche cubre las horas de la madrugada")
        void cruzaMedianoche() {
            // El turno de la 1 AM del sábado se agenda como parte de la noche del viernes.
            when(promocionCanchaRepository.findByCanchaIdAndActivoTrue(1L))
                    .thenReturn(List.of(promo("Nocturno", "5,6", "20:00", "02:00", "32000")));

            assertThat(promocionCanchaService.precioPorHora(1L, VIERNES, LocalTime.of(22, 0)))
                    .isEqualByComparingTo("32000");
            assertThat(promocionCanchaService.precioPorHora(1L, VIERNES, LocalTime.of(1, 0)))
                    .isEqualByComparingTo("32000");
            assertThat(promocionCanchaService.precioPorHora(1L, VIERNES, LocalTime.of(19, 0))).isNull();
        }

        @Test
        @DisplayName("Una promoción desactivada no se aplica")
        void desactivada_noAplica() {
            PromocionCancha inactiva = promo("Vieja", "1,2,3,4,5", "08:00", "23:00", "9000");
            inactiva.setActivo(false);
            when(promocionCanchaRepository.findByCanchaIdAndActivoTrue(1L)).thenReturn(List.of(inactiva));

            assertThat(promocionCanchaService.precioPorHora(1L, MARTES, LocalTime.of(15, 0))).isNull();
        }
    }

    @Nested
    @DisplayName("Validación al cargar")
    class Validacion {

        @BeforeEach
        void setUp() {
            lenient().when(canchaRepository.findById(1L)).thenReturn(Optional.of(cancha));
        }

        private PromocionCanchaRequest pedido(String dias, String desde, String hasta) {
            PromocionCanchaRequest request = new PromocionCanchaRequest();
            request.setCanchaId(1L);
            request.setNombre("Nueva");
            request.setDiasSemana(dias);
            request.setHoraDesde(LocalTime.parse(desde));
            request.setHoraHasta(LocalTime.parse(hasta));
            request.setPrecioPorHora(new BigDecimal("20000"));
            return request;
        }

        @Test
        @DisplayName("Rechaza una promoción que se superpone con otra del mismo día")
        void rechazaSuperposicion() {
            when(promocionCanchaRepository.findByCanchaIdAndActivoTrue(1L))
                    .thenReturn(List.of(promo("Pico", "1,2,3", "18:00", "23:00", "26000")));

            // Dos promociones superpuestas dejarían el precio librado al orden de la consulta.
            assertThatThrownBy(() -> promocionCanchaService.crear(pedido("3,4", "20:00", "22:00")))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("Pico");
            verify(promocionCanchaRepository, never()).save(any());
        }

        @Test
        @DisplayName("Permite promociones contiguas que no se pisan")
        void permiteContiguas() {
            when(promocionCanchaRepository.findByCanchaIdAndActivoTrue(1L))
                    .thenReturn(List.of(promo("Valle", "1,2,3", "08:00", "18:00", "14000")));

            promocionCanchaService.crear(pedido("1,2,3", "18:00", "23:00"));

            verify(promocionCanchaRepository).save(any(PromocionCancha.class));
        }

        @Test
        @DisplayName("Permite el mismo horario en días distintos")
        void permiteMismoHorarioOtroDia() {
            when(promocionCanchaRepository.findByCanchaIdAndActivoTrue(1L))
                    .thenReturn(List.of(promo("Semana", "1,2,3,4,5", "18:00", "23:00", "26000")));

            promocionCanchaService.crear(pedido("6,7", "18:00", "23:00"));

            verify(promocionCanchaRepository).save(any(PromocionCancha.class));
        }

        @Test
        @DisplayName("Detecta la superposición de una promoción que cruza medianoche")
        void detectaSuperposicionCruzandoMedianoche() {
            when(promocionCanchaRepository.findByCanchaIdAndActivoTrue(1L))
                    .thenReturn(List.of(promo("Nocturno", "5,6", "20:00", "02:00", "32000")));

            assertThatThrownBy(() -> promocionCanchaService.crear(pedido("5", "00:00", "01:00")))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("Nocturno");
        }

        @Test
        @DisplayName("Rechaza una promoción sin duración")
        void rechazaPromocionSinDuracion() {
            assertThatThrownBy(() -> promocionCanchaService.crear(pedido("1", "20:00", "20:00")))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("tiene que durar");
        }
    }
}
