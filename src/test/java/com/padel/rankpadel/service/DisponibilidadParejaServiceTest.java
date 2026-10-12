package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.padel.rankpadel.dto.RestriccionHorariaDto;
import com.padel.rankpadel.entity.RestriccionHoraria;
import com.padel.rankpadel.exception.EstadoInvalidoException;

@DisplayName("Disponibilidad de parejas - cuándo no pueden jugar")
class DisponibilidadParejaServiceTest {

    private static final LocalDate SABADO = LocalDate.of(2026, 11, 7);

    @Test
    @DisplayName("Un horario es de un día del torneo o de un día de la semana, no de los dos")
    void diaOFecha() {
        assertThatThrownBy(() -> DisponibilidadParejaService.validar(List.of(
                new RestriccionHorariaDto(SABADO, 6, LocalTime.of(9, 0), LocalTime.of(14, 0)))))
                .isInstanceOf(EstadoInvalidoException.class);
        assertThatThrownBy(() -> DisponibilidadParejaService.validar(List.of(
                new RestriccionHorariaDto(null, null, LocalTime.of(9, 0), LocalTime.of(14, 0)))))
                .isInstanceOf(EstadoInvalidoException.class);
    }

    @Test
    @DisplayName("No acepta un horario que termina antes de empezar")
    void alReves() {
        assertThatThrownBy(() -> DisponibilidadParejaService.validar(List.of(
                new RestriccionHorariaDto(SABADO, null, LocalTime.of(20, 0), LocalTime.of(9, 0)))))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessageContaining("23:59");
    }

    @Test
    @DisplayName("Un partido que se pisa aunque sea un rato con el horario cuenta como que no pueden")
    void pisa() {
        RestriccionHoraria sabadoManana = RestriccionHoraria.builder().fecha(SABADO)
                .horaDesde(LocalTime.of(9, 0)).horaHasta(LocalTime.of(14, 0)).build();

        assertThat(sabadoManana.pisa(SABADO.atTime(13, 0), SABADO.atTime(14, 30))).isTrue();
        assertThat(sabadoManana.pisa(SABADO.atTime(14, 0), SABADO.atTime(15, 30))).isFalse();
        assertThat(sabadoManana.pisa(SABADO.atTime(7, 30), SABADO.atTime(9, 0))).isFalse();
        assertThat(sabadoManana.pisa(SABADO.plusDays(1).atTime(10, 0), SABADO.plusDays(1).atTime(11, 30))).isFalse();
    }

    @Test
    @DisplayName("Un día de la semana se repite todas las semanas (ligas)")
    void diaDeLaSemana() {
        RestriccionHoraria martesNoche = RestriccionHoraria.builder().diaSemana(2)
                .horaDesde(LocalTime.of(20, 0)).horaHasta(LocalTime.of(23, 59)).build();
        LocalDateTime unMartes = LocalDate.of(2026, 11, 10).atTime(21, 0);

        assertThat(martesNoche.pisa(unMartes, unMartes.plusMinutes(90))).isTrue();
        assertThat(martesNoche.pisa(unMartes.plusWeeks(3), unMartes.plusWeeks(3).plusMinutes(90))).isTrue();
        assertThat(martesNoche.pisa(unMartes.plusDays(1), unMartes.plusDays(1).plusMinutes(90))).isFalse();
    }

    @Test
    @DisplayName("Se describe en palabras para el panel")
    void describir() {
        assertThat(DisponibilidadParejaService.describir(RestriccionHoraria.builder().fecha(SABADO)
                .horaDesde(LocalTime.of(9, 0)).horaHasta(LocalTime.of(14, 0)).build()))
                .isEqualTo("sábado 7/11 de 09:00 a 14:00");
        assertThat(DisponibilidadParejaService.describir(RestriccionHoraria.builder().diaSemana(2)
                .horaDesde(LocalTime.of(20, 0)).horaHasta(LocalTime.of(23, 59)).build()))
                .isEqualTo("los martes de 20:00 a 23:59");
    }
}
