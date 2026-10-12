package com.padel.rankpadel.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.padel.rankpadel.entity.RestriccionHoraria;
import com.padel.rankpadel.service.ProgramadorFixture.Franja;
import com.padel.rankpadel.service.ProgramadorFixture.Ocupacion;

/**
 * Lo que está mal (o es dudoso) en los horarios que ya tiene el torneo. El organizador
 * mueve partidos a mano, se le cae un club, una pareja avisa que no puede: esto le dice
 * qué quedó roto.
 *
 * <p>Los graves no se pueden jugar así (dos partidos en la misma cancha, un jugador en dos
 * lados, una cancha reservada). Los avisos se pueden jugar pero conviene mirarlos.
 */
public final class ConflictosFixture {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("d/M");

    private ConflictosFixture() {
    }

    public enum Tipo {
        CANCHA_SUPERPUESTA(true), JUGADOR_SUPERPUESTO(true), CANCHA_RESERVADA(true),
        SIN_DESCANSO(false), RESTRICCION(false), FUERA_DE_FRANJA(false), CANCHA_AJENA(false),
        CLUB_FIJO(false), SIN_CANCHA(false);

        private final boolean grave;

        Tipo(boolean grave) {
            this.grave = grave;
        }

        public boolean esGrave() {
            return grave;
        }
    }

    /** Un partido con horario, ya con todo lo que hace falta para revisarlo. */
    public record PartidoAgendado(Long id, String descripcion, Long canchaId, String canchaNombre, Long lugarId,
            LocalDateTime inicio, LocalDateTime fin, Map<Long, String> jugadores,
            String parejaLocal, List<RestriccionHoraria> restriccionesLocal,
            String parejaVisitante, List<RestriccionHoraria> restriccionesVisitante,
            Long lugarFijoId, String lugarFijoNombre) {
    }

    public record Conflicto(Tipo tipo, List<Long> partidoIds, String mensaje) {
        public boolean grave() {
            return tipo.esGrave();
        }
    }

    /**
     * @param propios  los partidos del torneo con horario
     * @param ajenos   partidos de otros torneos en las mismas canchas (solo se mira la cancha)
     * @param franjas  lo que cedió cada club; una cancha sin franjas no se revisa por horario
     * @param reservas turnos y bloqueos de las canchas (en un organizador, vacío)
     * @param canchasDelTorneo las canchas que cedieron los clubes del torneo
     */
    public static List<Conflicto> revisar(List<PartidoAgendado> propios, List<PartidoAgendado> ajenos,
            List<Franja> franjas, Map<Long, List<Ocupacion>> reservas, Set<Long> canchasDelTorneo, int descanso) {
        List<Conflicto> conflictos = new ArrayList<>();

        for (PartidoAgendado p : propios) {
            if (p.canchaId() == null) {
                conflictos.add(new Conflicto(Tipo.SIN_CANCHA, List.of(p.id()),
                        p.descripcion() + " tiene horario pero no cancha."));
                continue;
            }
            if (!canchasDelTorneo.contains(p.canchaId())) {
                conflictos.add(new Conflicto(Tipo.CANCHA_AJENA, List.of(p.id()), p.descripcion() + " está en "
                        + p.canchaNombre() + ", que no es de los clubes del torneo."));
            }
            List<Franja> deLaCancha = franjas.stream().filter(f -> f.canchaId().equals(p.canchaId())).toList();
            if (!deLaCancha.isEmpty() && deLaCancha.stream()
                    .noneMatch(f -> !p.inicio().isBefore(f.inicio()) && !p.fin().isAfter(f.fin()))) {
                conflictos.add(new Conflicto(Tipo.FUERA_DE_FRANJA, List.of(p.id()), p.descripcion() + " ("
                        + cuando(p) + ") queda fuera de los horarios que cedió el club."));
            }
            for (Ocupacion reserva : reservas.getOrDefault(p.canchaId(), List.of())) {
                if (sePisan(p.inicio(), p.fin(), reserva.inicio(), reserva.fin())) {
                    conflictos.add(new Conflicto(Tipo.CANCHA_RESERVADA, List.of(p.id()), p.descripcion() + " pisa un turno "
                            + "reservado o un bloqueo de " + p.canchaNombre() + " (" + hora(reserva.inicio()) + " a "
                            + hora(reserva.fin()) + ")."));
                    break;
                }
            }
            if (p.lugarFijoId() != null && !p.lugarFijoId().equals(p.lugarId())) {
                conflictos.add(new Conflicto(Tipo.CLUB_FIJO, List.of(p.id()), p.descripcion()
                        + " no está en " + p.lugarFijoNombre() + ", el club fijado para su categoría."));
            }
            restriccion(p, p.parejaLocal(), p.restriccionesLocal(), conflictos);
            restriccion(p, p.parejaVisitante(), p.restriccionesVisitante(), conflictos);
        }

        for (int i = 0; i < propios.size(); i++) {
            PartidoAgendado a = propios.get(i);
            for (int j = i + 1; j < propios.size(); j++) {
                PartidoAgendado b = propios.get(j);
                if (a.canchaId() != null && a.canchaId().equals(b.canchaId()) && sePisan(a.inicio(), a.fin(), b.inicio(), b.fin())) {
                    conflictos.add(new Conflicto(Tipo.CANCHA_SUPERPUESTA, List.of(a.id(), b.id()), "En " + a.canchaNombre()
                            + " se pisan " + a.descripcion() + " (" + cuando(a) + ") y " + b.descripcion() + " (" + hora(b.inicio()) + ")."));
                }
                List<String> enLosDos = a.jugadores().keySet().stream()
                        .filter(b.jugadores()::containsKey)
                        .map(a.jugadores()::get)
                        .toList();
                if (enLosDos.isEmpty()) {
                    continue;
                }
                String quienes = String.join(" y ", enLosDos);
                if (sePisan(a.inicio(), a.fin(), b.inicio(), b.fin())) {
                    conflictos.add(new Conflicto(Tipo.JUGADOR_SUPERPUESTO, List.of(a.id(), b.id()), quienes
                            + (enLosDos.size() > 1 ? " juegan" : " juega") + " dos partidos a la vez: " + a.descripcion()
                            + " (" + cuando(a) + ") y " + b.descripcion() + " (" + hora(b.inicio()) + ")."));
                } else {
                    boolean aPrimero = !a.fin().isAfter(b.inicio());
                    long hueco = aPrimero
                            ? Duration.between(a.fin(), b.inicio()).toMinutes()
                            : Duration.between(b.fin(), a.inicio()).toMinutes();
                    if (hueco < descanso) {
                        conflictos.add(new Conflicto(Tipo.SIN_DESCANSO, List.of(a.id(), b.id()), quienes + " "
                                + (enLosDos.size() > 1 ? "tienen" : "tiene") + " " + hueco + " min entre "
                                + a.descripcion() + " y " + b.descripcion() + " (el mínimo es " + descanso + ")."));
                    }
                }
            }
            for (PartidoAgendado otro : ajenos) {
                if (a.canchaId() != null && a.canchaId().equals(otro.canchaId()) && sePisan(a.inicio(), a.fin(), otro.inicio(), otro.fin())) {
                    conflictos.add(new Conflicto(Tipo.CANCHA_SUPERPUESTA, List.of(a.id()), "En " + a.canchaNombre() + " "
                            + a.descripcion() + " (" + cuando(a) + ") se pisa con " + otro.descripcion() + "."));
                }
            }
        }

        conflictos.sort((x, y) -> Boolean.compare(y.grave(), x.grave()));
        return conflictos;
    }

    private static void restriccion(PartidoAgendado p, String pareja, List<RestriccionHoraria> restricciones,
            List<Conflicto> conflictos) {
        List<RestriccionHoraria> pisadas = restricciones.stream().filter(r -> r.pisa(p.inicio(), p.fin())).toList();
        if (!pisadas.isEmpty()) {
            conflictos.add(new Conflicto(Tipo.RESTRICCION, List.of(p.id()), pareja + " dijeron que no pueden "
                    + pisadas.stream().map(DisponibilidadParejaService::describir).collect(Collectors.joining(", "))
                    + ", y " + p.descripcion() + " es " + cuando(p) + "."));
        }
    }

    static boolean sePisan(LocalDateTime inicioA, LocalDateTime finA, LocalDateTime inicioB, LocalDateTime finB) {
        return inicioA.isBefore(finB) && inicioB.isBefore(finA);
    }

    private static String cuando(PartidoAgendado p) {
        return "el " + DIA.format(p.inicio()) + " a las " + hora(p.inicio());
    }

    private static String hora(LocalDateTime momento) {
        return HORA.format(momento);
    }
}
