package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.padel.rankpadel.entity.RestriccionHoraria;
import com.padel.rankpadel.service.ConflictosFixture.Conflicto;
import com.padel.rankpadel.service.ConflictosFixture.PartidoAgendado;
import com.padel.rankpadel.service.ConflictosFixture.Tipo;
import com.padel.rankpadel.service.ProgramadorFixture.Franja;
import com.padel.rankpadel.service.ProgramadorFixture.Ocupacion;

@DisplayName("ConflictosFixture - qué quedó roto en los horarios")
class ConflictosFixtureTest {

    private static final LocalDate SABADO = LocalDate.of(2026, 11, 7);

    private static PartidoAgendado partido(long id, long cancha, int hora, int minutos, Map<Long, String> jugadores) {
        LocalDateTime inicio = SABADO.atTime(hora, minutos);
        return new PartidoAgendado(id, "Partido " + id, cancha, "Cancha " + cancha, 1L, inicio, inicio.plusMinutes(90),
                jugadores, "Local", List.of(), "Visitante", List.of(), null, null);
    }

    private static List<Conflicto> revisar(List<PartidoAgendado> partidos) {
        return ConflictosFixture.revisar(partidos, List.of(), List.of(), Map.of(), Set.of(10L, 11L), 30);
    }

    private static List<Tipo> tipos(List<Conflicto> conflictos) {
        return conflictos.stream().map(Conflicto::tipo).toList();
    }

    @Test
    @DisplayName("Sin choques no hay nada que decir")
    void limpio() {
        assertThat(revisar(List.of(
                partido(1, 10, 9, 0, Map.of(1L, "Ana", 2L, "Bea")),
                partido(2, 10, 10, 30, Map.of(3L, "Caro", 4L, "Dani")),
                partido(3, 11, 9, 0, Map.of(5L, "Eli", 6L, "Fer"))))).isEmpty();
    }

    @Test
    @DisplayName("Dos partidos en la misma cancha que se pisan es grave")
    void canchaSuperpuesta() {
        List<Conflicto> conflictos = revisar(List.of(
                partido(1, 10, 9, 0, Map.of(1L, "Ana")),
                partido(2, 10, 10, 0, Map.of(2L, "Bea"))));

        assertThat(tipos(conflictos)).containsExactly(Tipo.CANCHA_SUPERPUESTA);
        assertThat(conflictos.get(0).grave()).isTrue();
        assertThat(conflictos.get(0).partidoIds()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("Un jugador en dos canchas a la vez es grave; con poco descanso, aviso")
    void jugador() {
        List<Conflicto> aLaVez = revisar(List.of(
                partido(1, 10, 9, 0, Map.of(1L, "Ana Paz")),
                partido(2, 11, 10, 0, Map.of(1L, "Ana Paz"))));
        assertThat(tipos(aLaVez)).containsExactly(Tipo.JUGADOR_SUPERPUESTO);
        assertThat(aLaVez.get(0).mensaje()).startsWith("Ana Paz juega dos partidos a la vez");

        List<Conflicto> pegados = revisar(List.of(
                partido(1, 10, 9, 0, Map.of(1L, "Ana Paz")),
                partido(2, 11, 10, 30, Map.of(1L, "Ana Paz"))));
        assertThat(tipos(pegados)).containsExactly(Tipo.SIN_DESCANSO);
        assertThat(pegados.get(0).grave()).isFalse();
        assertThat(pegados.get(0).mensaje()).contains("0 min");

        assertThat(revisar(List.of(
                partido(2, 11, 11, 0, Map.of(1L, "Ana Paz")),
                partido(1, 10, 9, 0, Map.of(1L, "Ana Paz"))))).isEmpty();
    }

    @Test
    @DisplayName("Avisa si se programó cuando la pareja dijo que no puede")
    void restriccion() {
        RestriccionHoraria manana = RestriccionHoraria.builder().fecha(SABADO)
                .horaDesde(LocalTime.of(9, 0)).horaHasta(LocalTime.of(12, 0)).build();
        LocalDateTime inicio = SABADO.atTime(10, 0);
        PartidoAgendado p = new PartidoAgendado(1L, "Partido 1", 10L, "Cancha 10", 1L, inicio, inicio.plusMinutes(90),
                Map.of(), "Paz/Sol", List.of(manana), "Visitante", List.of(), null, null);

        List<Conflicto> conflictos = revisar(List.of(p));

        assertThat(tipos(conflictos)).containsExactly(Tipo.RESTRICCION);
        assertThat(conflictos.get(0).mensaje()).startsWith("Paz/Sol dijeron que no pueden sábado 7/11 de 09:00 a 12:00");
    }

    @Test
    @DisplayName("Fuera de las franjas del club, en una cancha que no es del torneo o sin cancha: avisos")
    void ubicacion() {
        List<Franja> franjas = List.of(new Franja(10L, 1L, SABADO.atTime(14, 0), SABADO.atTime(22, 0)));
        LocalDateTime nueve = SABADO.atTime(9, 0);
        PartidoAgendado sinCancha = new PartidoAgendado(3L, "Partido 3", null, null, null, nueve, nueve.plusMinutes(90),
                Map.of(), "L", List.of(), "V", List.of(), null, null);

        List<Conflicto> conflictos = ConflictosFixture.revisar(List.of(
                partido(1, 10, 9, 0, Map.of(1L, "Ana")),
                partido(2, 99, 15, 0, Map.of(2L, "Bea")),
                sinCancha), List.of(), franjas, Map.of(), Set.of(10L), 30);

        assertThat(tipos(conflictos)).containsExactlyInAnyOrder(Tipo.FUERA_DE_FRANJA, Tipo.CANCHA_AJENA, Tipo.SIN_CANCHA);
        assertThat(conflictos).noneMatch(Conflicto::grave);
    }

    @Test
    @DisplayName("Pisar un turno reservado o un partido de otro torneo es grave")
    void ajenosYReservas() {
        PartidoAgendado otroTorneo = partido(50, 11, 9, 30, Map.of(9L, "Zoe"));
        Map<Long, List<Ocupacion>> reservas = Map.of(10L, List.of(new Ocupacion(SABADO.atTime(10, 0), SABADO.atTime(11, 0))));

        List<Conflicto> conflictos = ConflictosFixture.revisar(List.of(
                partido(1, 10, 9, 0, Map.of(1L, "Ana")),
                partido(2, 11, 9, 0, Map.of(2L, "Bea"))), List.of(otroTorneo), List.of(), reservas, Set.of(10L, 11L), 30);

        assertThat(tipos(conflictos)).containsExactlyInAnyOrder(Tipo.CANCHA_RESERVADA, Tipo.CANCHA_SUPERPUESTA);
        assertThat(conflictos).allMatch(Conflicto::grave);
    }

    @Test
    @DisplayName("Los graves van primero")
    void orden() {
        List<Conflicto> conflictos = revisar(List.of(
                partido(1, 10, 9, 0, Map.of(1L, "Ana")),
                partido(2, 11, 10, 40, Map.of(1L, "Ana")),
                partido(3, 11, 12, 0, Map.of(7L, "Gus")),
                partido(4, 11, 12, 30, Map.of(8L, "Hugo"))));

        assertThat(conflictos.get(0).grave()).isTrue();
        assertThat(conflictos.get(conflictos.size() - 1).grave()).isFalse();
    }
}
