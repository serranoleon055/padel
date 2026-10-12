package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.padel.rankpadel.entity.RestriccionHoraria;
import com.padel.rankpadel.service.ProgramadorFixture.Asignacion;
import com.padel.rankpadel.service.ProgramadorFixture.Franja;
import com.padel.rankpadel.service.ProgramadorFixture.Ocupacion;
import com.padel.rankpadel.service.ProgramadorFixture.PartidoFijo;
import com.padel.rankpadel.service.ProgramadorFixture.PartidoPendiente;
import com.padel.rankpadel.service.ProgramadorFixture.Resultado;

@DisplayName("ProgramadorFixture - el armado de horarios")
class ProgramadorFixtureTest {

    private static final LocalDate SABADO = LocalDate.of(2026, 11, 7);
    private static final LocalDateTime DESDE = SABADO.atStartOfDay();
    private static final long CLUB_A = 1L;
    private static final long CLUB_B = 2L;

    private static Franja franja(long cancha, long club, int desde, int hasta) {
        return new Franja(cancha, club, SABADO.atTime(desde, 0), SABADO.atTime(hasta, 0));
    }

    private static PartidoPendiente partido(long id, Set<Long> jugadores, int duracion) {
        return new PartidoPendiente(id, jugadores, List.of(), duracion, null, 1);
    }

    private static Resultado programar(List<Franja> franjas, List<PartidoPendiente> pendientes, int descanso) {
        return ProgramadorFixture.programar(franjas, Map.of(), List.of(), pendientes, descanso, DESDE);
    }

    @Test
    @DisplayName("Llena las canchas en paralelo desde el principio de la franja")
    void enParalelo() {
        Resultado r = programar(List.of(franja(10, CLUB_A, 9, 13), franja(11, CLUB_A, 9, 13)),
                List.of(partido(1, Set.of(1L, 2L, 3L, 4L), 90), partido(2, Set.of(5L, 6L, 7L, 8L), 90)), 30);

        assertThat(r.sinLugar()).isEmpty();
        assertThat(r.asignaciones()).extracting(Asignacion::inicio).containsOnly(SABADO.atTime(9, 0));
        assertThat(r.asignaciones()).extracting(Asignacion::canchaId).containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    @DisplayName("Un jugador no juega dos partidos a la vez ni sin descanso, aunque sea en otra categoría")
    void descansoDelJugador() {
        // El jugador 1 juega en los dos partidos (dos categorías distintas).
        Resultado r = programar(List.of(franja(10, CLUB_A, 9, 18), franja(11, CLUB_A, 9, 18)),
                List.of(partido(1, Set.of(1L, 2L, 3L, 4L), 60), partido(2, Set.of(1L, 9L, 7L, 8L), 60)), 30);

        Map<Long, LocalDateTime> inicio = porPartido(r);
        assertThat(inicio.get(1L)).isEqualTo(SABADO.atTime(9, 0));
        assertThat(inicio.get(2L)).isEqualTo(SABADO.atTime(10, 30));
    }

    @Test
    @DisplayName("Respeta los horarios en que una pareja dijo que no puede")
    void restricciones() {
        RestriccionHoraria manana = RestriccionHoraria.builder().fecha(SABADO)
                .horaDesde(LocalTime.of(9, 0)).horaHasta(LocalTime.of(14, 0)).build();
        PartidoPendiente conRestriccion = new PartidoPendiente(1L, Set.of(1L, 2L, 3L, 4L), List.of(manana), 90, null, 1);

        Resultado r = programar(List.of(franja(10, CLUB_A, 9, 20)), List.of(conRestriccion), 30);

        assertThat(r.asignaciones().get(0).inicio()).isEqualTo(SABADO.atTime(14, 0));
    }

    @Test
    @DisplayName("Una categoría fijada a un club solo usa canchas de ese club")
    void clubFijo() {
        PartidoPendiente enB = new PartidoPendiente(1L, Set.of(1L, 2L, 3L, 4L), List.of(), 90, CLUB_B, 1);

        Resultado r = programar(List.of(franja(10, CLUB_A, 9, 20), franja(20, CLUB_B, 15, 20)), List.of(enB), 30);

        assertThat(r.asignaciones().get(0).canchaId()).isEqualTo(20L);
        assertThat(r.asignaciones().get(0).inicio()).isEqualTo(SABADO.atTime(15, 0));
    }

    @Test
    @DisplayName("No pisa partidos ya programados ni reservas de la cancha")
    void respetaLoOcupado() {
        PartidoFijo yaEsta = new PartidoFijo(10L, CLUB_A, Set.of(50L, 51L, 52L, 53L), SABADO.atTime(9, 0), SABADO.atTime(10, 30));
        Map<Long, List<Ocupacion>> reservas = Map.of(10L, List.of(new Ocupacion(SABADO.atTime(10, 30), SABADO.atTime(12, 0))));

        Resultado r = ProgramadorFixture.programar(List.of(franja(10, CLUB_A, 9, 18)), reservas, List.of(yaEsta),
                List.of(partido(1, Set.of(1L, 2L, 3L, 4L), 90)), 30, DESDE);

        assertThat(r.asignaciones().get(0).inicio()).isEqualTo(SABADO.atTime(12, 0));
    }

    @Test
    @DisplayName("Las fechas de zona van en orden aunque haya lugar antes")
    void prioridad() {
        PartidoPendiente fecha2 = new PartidoPendiente(1L, Set.of(1L, 2L, 3L, 4L), List.of(), 60, null, 2);
        PartidoPendiente fecha1 = new PartidoPendiente(2L, Set.of(1L, 2L, 5L, 6L), List.of(), 60, null, 1);

        Map<Long, LocalDateTime> inicio = porPartido(programar(List.of(franja(10, CLUB_A, 9, 18)), List.of(fecha2, fecha1), 30));

        assertThat(inicio.get(2L)).isBefore(inicio.get(1L));
    }

    @Test
    @DisplayName("Si no entra, dice por qué")
    void sinLugar() {
        RestriccionHoraria todoElDia = RestriccionHoraria.builder().fecha(SABADO)
                .horaDesde(LocalTime.of(0, 0)).horaHasta(LocalTime.of(23, 59)).build();
        PartidoPendiente noPueden = new PartidoPendiente(1L, Set.of(1L, 2L, 3L, 4L), List.of(todoElDia), 90, null, 1);
        PartidoPendiente noEntra = partido(2, Set.of(5L, 6L, 7L, 8L), 90);
        PartidoPendiente tampoco = partido(3, Set.of(9L, 10L, 11L, 12L), 90);

        Resultado r = programar(List.of(franja(10, CLUB_A, 9, 11)), List.of(noPueden, noEntra, tampoco), 30);

        assertThat(r.asignaciones()).hasSize(1);
        assertThat(r.sinLugar()).extracting(s -> s.motivo().substring(0, 20))
                .containsExactlyInAnyOrder("Las parejas dijeron ", "No quedan canchas li");
    }

    @Test
    @DisplayName("Entre dos horarios iguales, no hace cambiar de club en el día")
    void mismoClub() {
        // El jugador 1 ya juega a las 9 en el club B; a las 11 hay lugar en A y en B.
        PartidoFijo enB = new PartidoFijo(20L, CLUB_B, Set.of(1L, 2L, 3L, 4L), SABADO.atTime(9, 0), SABADO.atTime(10, 30));
        Resultado r = ProgramadorFixture.programar(List.of(franja(10, CLUB_A, 11, 18), franja(20, CLUB_B, 9, 18)),
                Map.of(), List.of(enB), List.of(partido(1, Set.of(1L, 2L, 7L, 8L), 90)), 30, DESDE);

        assertThat(r.asignaciones().get(0).canchaId()).isEqualTo(20L);
        assertThat(r.asignaciones().get(0).inicio()).isEqualTo(SABADO.atTime(11, 0));
    }

    @Test
    @DisplayName("No programa antes del momento indicado y arranca en la grilla de la franja")
    void desde() {
        Resultado r = ProgramadorFixture.programar(List.of(franja(10, CLUB_A, 9, 18)), Map.of(), List.of(),
                List.of(partido(1, Set.of(1L, 2L, 3L, 4L), 60)), 30, SABADO.atTime(10, 7));

        assertThat(r.asignaciones().get(0).inicio()).isEqualTo(SABADO.atTime(10, 15));
    }

    @Test
    @DisplayName("Un torneo de 16 parejas en zonas entra entero y sin choques")
    void torneoCompleto() {
        List<Franja> franjas = new ArrayList<>();
        for (long cancha = 10; cancha < 13; cancha++) {
            franjas.add(franja(cancha, CLUB_A, 9, 23));
        }
        // 4 zonas de 4 parejas: 6 partidos cada una, en 3 fechas.
        List<PartidoPendiente> pendientes = new ArrayList<>();
        long id = 1;
        int[][] fechas = { { 0, 1, 2, 3 }, { 0, 2, 1, 3 }, { 0, 3, 1, 2 } };
        for (int zona = 0; zona < 4; zona++) {
            for (int fecha = 0; fecha < 3; fecha++) {
                for (int cruce = 0; cruce < 2; cruce++) {
                    long a = zona * 4L + fechas[fecha][cruce * 2];
                    long b = zona * 4L + fechas[fecha][cruce * 2 + 1];
                    pendientes.add(new PartidoPendiente(id++, Set.of(a * 2, a * 2 + 1, b * 2, b * 2 + 1),
                            List.of(), 60, null, fecha + 1));
                }
            }
        }

        Resultado r = programar(franjas, pendientes, 30);

        assertThat(r.sinLugar()).isEmpty();
        assertThat(r.asignaciones()).hasSize(24);
        // Nadie superpuesto: ni cancha ni jugador.
        Map<Long, PartidoPendiente> porId = new HashMap<>();
        pendientes.forEach(p -> porId.put(p.id(), p));
        for (Asignacion x : r.asignaciones()) {
            for (Asignacion y : r.asignaciones()) {
                if (x == y || !x.inicio().isBefore(y.fin()) || !y.inicio().isBefore(x.fin())) {
                    continue;
                }
                assertThat(x.canchaId()).isNotEqualTo(y.canchaId());
                assertThat(porId.get(x.partidoId()).jugadores()).doesNotContainAnyElementsOf(porId.get(y.partidoId()).jugadores());
            }
        }
    }

    @Test
    @DisplayName("Reacomoda: corre al partido flexible para que entre el que solo tiene un horario")
    void reacomodo() {
        LocalDate domingo = SABADO.plusDays(1);
        List<Franja> franjas = List.of(
                franja(10, CLUB_A, 14, 16),
                new Franja(10L, CLUB_A, domingo.atTime(9, 0), domingo.atTime(11, 0)),
                franja(20, CLUB_B, 14, 16));
        // El jugador 1 está en las dos categorías. La de A puede el domingo; la de B, fijada
        // al club B, solo tiene el sábado a la tarde. A va primero por prioridad y se queda
        // con el sábado: sin reacomodo, B no entra.
        PartidoPendiente flexible = new PartidoPendiente(1L, Set.of(1L, 2L, 3L, 4L), List.of(), 60, null, 1);
        PartidoPendiente fijado = new PartidoPendiente(2L, Set.of(1L, 5L, 6L, 7L), List.of(), 60, CLUB_B, 2);

        Resultado r = programar(franjas, List.of(flexible, fijado), 30);

        assertThat(r.sinLugar()).isEmpty();
        Map<Long, LocalDateTime> inicio = porPartido(r);
        assertThat(inicio.get(2L)).isEqualTo(SABADO.atTime(14, 0));
        assertThat(inicio.get(1L)).isEqualTo(domingo.atTime(9, 0));
    }

    @Test
    @DisplayName("Si reacomodar no alcanza, deja todo como estaba")
    void reacomodoImposible() {
        PartidoPendiente a = partido(1, Set.of(1L, 2L, 3L, 4L), 120);
        PartidoPendiente b = partido(2, Set.of(5L, 6L, 7L, 8L), 120);

        Resultado r = programar(List.of(franja(10, CLUB_A, 9, 11)), List.of(a, b), 30);

        assertThat(r.asignaciones()).extracting(Asignacion::partidoId).containsExactly(1L);
        assertThat(r.sinLugar()).hasSize(1);
    }

    private static Map<Long, LocalDateTime> porPartido(Resultado r) {
        Map<Long, LocalDateTime> inicio = new HashMap<>();
        r.asignaciones().forEach(a -> inicio.put(a.partidoId(), a.inicio()));
        return inicio;
    }
}
