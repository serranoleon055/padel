package com.padel.rankpadel.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.padel.rankpadel.entity.RestriccionHoraria;

/**
 * El que arma los horarios. No sabe nada de la base: recibe las canchas con sus franjas,
 * lo que ya está ocupado y los partidos a ubicar, y devuelve dónde va cada uno.
 *
 * <p>Va partido por partido y a cada uno le da el primer hueco que cumple todo:
 * <ul>
 * <li>la cancha está libre (ni otro partido, de este torneo o de otro, ni una reserva);</li>
 * <li>el turno entra entero en una franja que el club cedió;</li>
 * <li>si la categoría está fijada a un club, es una cancha de ese club;</li>
 * <li>ninguna de las dos parejas dijo que no puede a esa hora;</li>
 * <li>ninguno de los cuatro jugadores está jugando otro partido (aunque sea de otra
 * categoría) ni le falta el descanso mínimo entre uno y otro.</li>
 * </ul>
 *
 * <p>El orden importa: primero las fechas de zona en orden (la 1 antes que la 2), después
 * la llave; y dentro de cada tanda, los partidos con menos huecos posibles primero, que son
 * los que se quedarían sin lugar si los demás ocupan las canchas antes. Entre dos huecos a
 * la misma hora gana el que no obliga a una pareja a cambiar de club en el día.
 *
 * <p>Ir de a uno a veces encierra a un partido difícil: un jugador que está en dos
 * categorías llena la tarde del sábado con partidos que podían ir el domingo, y el de la
 * categoría que solo tiene el sábado se queda afuera. Para eso hay una segunda vuelta: al
 * que no entró se le hace lugar sacando un partido que lo traba y reubicándolo en otro
 * hueco. Si el sacado no encuentra lugar, se deshace el cambio.
 */
public final class ProgramadorFixture {

    /** Cada cuánto puede arrancar un partido dentro de una franja. */
    static final int PASO_MINUTOS = 15;

    /** Cuánto "cuesta" hacer que una pareja cambie de club en el mismo día. */
    private static final int PENALIDAD_CAMBIO_DE_CLUB = 60;

    /** Vueltas de reacomodo para los que no entraron. */
    private static final int VUELTAS_DE_REACOMODO = 3;

    private ProgramadorFixture() {
    }

    /** Un rato en que una cancha está disponible para el torneo. */
    public record Franja(Long canchaId, Long lugarId, LocalDateTime inicio, LocalDateTime fin) {
    }

    /** Un rato en que una cancha está ocupada por algo que no es de este armado. */
    public record Ocupacion(LocalDateTime inicio, LocalDateTime fin) {
    }

    /** Un partido que ya tiene horario y no se mueve. */
    public record PartidoFijo(Long canchaId, Long lugarId, Set<Long> jugadores, LocalDateTime inicio, LocalDateTime fin) {
    }

    /**
     * Un partido a ubicar.
     *
     * @param prioridad menor va antes (fecha de zona, ronda de la llave)
     */
    public record PartidoPendiente(Long id, Set<Long> jugadores, List<RestriccionHoraria> restricciones,
            int duracionMinutos, Long lugarFijoId, int prioridad) {
    }

    public record Asignacion(Long partidoId, Long canchaId, LocalDateTime inicio, LocalDateTime fin) {
    }

    public record SinLugar(Long partidoId, String motivo) {
    }

    public record Resultado(List<Asignacion> asignaciones, List<SinLugar> sinLugar) {
    }

    private record Uso(LocalDateTime inicio, LocalDateTime fin, Long lugarId) {
    }

    private record Candidato(Franja franja, LocalDateTime inicio) {
    }

    private record Colocado(PartidoPendiente partido, Candidato candidato, Uso uso) {
    }

    /** Lo ocupado hasta ahora, que se puede deshacer para reacomodar. */
    private static final class Estado {
        final List<Franja> franjas;
        final int descanso;
        final LocalDateTime desde;
        final Map<Long, List<Uso>> usoCancha = new HashMap<>();
        final Map<Long, List<Uso>> usoJugador = new HashMap<>();
        final Map<Long, Colocado> colocados = new LinkedHashMap<>();

        Estado(List<Franja> franjas, int descanso, LocalDateTime desde) {
            this.franjas = franjas;
            this.descanso = descanso;
            this.desde = desde;
        }

        /** El mejor hueco para el partido, o null con el motivo en {@code motivo[0]}. */
        Candidato mejor(PartidoPendiente partido, String[] motivo) {
            List<Candidato> candidatos = candidatos(franjas, partido, desde);
            if (candidatos.isEmpty()) {
                motivo[0] = partido.lugarFijoId() != null
                        ? "El club fijado para la categoría no tiene canchas ni horarios cargados para el torneo."
                        : "No hay horarios de canchas cargados en los clubes del torneo (o ya pasaron).";
                return null;
            }
            Candidato mejor = null;
            long mejorCosto = Long.MAX_VALUE;
            boolean algunoSinRestriccion = false;
            boolean algunaCanchaLibre = false;
            for (Candidato candidato : candidatos) {
                LocalDateTime inicio = candidato.inicio();
                LocalDateTime fin = inicio.plusMinutes(partido.duracionMinutos());
                if (!respetaRestricciones(partido, inicio)) {
                    continue;
                }
                algunoSinRestriccion = true;
                if (!libre(usoCancha.get(candidato.franja().canchaId()), inicio, fin, 0)) {
                    continue;
                }
                algunaCanchaLibre = true;
                if (!partido.jugadores().stream().allMatch(j -> libre(usoJugador.get(j), inicio, fin, descanso))) {
                    continue;
                }
                long costo = Duration.between(desde, inicio).toMinutes()
                        + (cambiaDeClub(partido, candidato) ? PENALIDAD_CAMBIO_DE_CLUB : 0);
                if (costo < mejorCosto) {
                    mejorCosto = costo;
                    mejor = candidato;
                }
            }
            if (mejor == null) {
                motivo[0] = !algunoSinRestriccion
                        ? "Las parejas dijeron que no pueden en ninguno de los horarios disponibles."
                        : !algunaCanchaLibre
                                ? "No quedan canchas libres en los horarios en que pueden las parejas."
                                : "Choca con otros partidos de los jugadores o no les deja el descanso mínimo.";
            }
            return mejor;
        }

        void ocupar(Long canchaId, Set<Long> jugadores, Uso uso) {
            if (canchaId != null) {
                usoCancha.computeIfAbsent(canchaId, k -> new ArrayList<>()).add(uso);
            }
            jugadores.forEach(j -> usoJugador.computeIfAbsent(j, k -> new ArrayList<>()).add(uso));
        }

        void colocar(PartidoPendiente partido, Candidato candidato) {
            Uso uso = new Uso(candidato.inicio(), candidato.inicio().plusMinutes(partido.duracionMinutos()),
                    candidato.franja().lugarId());
            ocupar(candidato.franja().canchaId(), partido.jugadores(), uso);
            colocados.put(partido.id(), new Colocado(partido, candidato, uso));
        }

        Colocado sacar(Long partidoId) {
            Colocado colocado = colocados.remove(partidoId);
            // Por identidad: dos usos pueden tener el mismo horario y lugar.
            usoCancha.get(colocado.candidato().franja().canchaId()).removeIf(u -> u == colocado.uso());
            colocado.partido().jugadores().forEach(j -> usoJugador.get(j).removeIf(u -> u == colocado.uso()));
            return colocado;
        }

        /** Si algún jugador ya juega ese mismo día en otro club. */
        private boolean cambiaDeClub(PartidoPendiente partido, Candidato candidato) {
            LocalDate dia = candidato.inicio().toLocalDate();
            Long lugar = candidato.franja().lugarId();
            return partido.jugadores().stream()
                    .map(usoJugador::get)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .anyMatch(uso -> uso.lugarId() != null && uso.inicio().toLocalDate().equals(dia)
                            && !uso.lugarId().equals(lugar));
        }
    }

    /**
     * @param ocupadas      reservas y bloqueos por cancha (en un organizador, vacío)
     * @param fijos         partidos con horario que no se tocan
     * @param descanso      minutos mínimos entre dos partidos del mismo jugador
     * @param desde         no se programa nada antes de este momento
     */
    public static Resultado programar(List<Franja> franjas, Map<Long, List<Ocupacion>> ocupadas,
            List<PartidoFijo> fijos, List<PartidoPendiente> pendientes, int descanso, LocalDateTime desde) {

        Estado estado = new Estado(franjas, descanso, desde);
        ocupadas.forEach((cancha, lista) -> lista.forEach(o ->
                estado.ocupar(cancha, Set.of(), new Uso(o.inicio(), o.fin(), null))));
        for (PartidoFijo fijo : fijos) {
            estado.ocupar(fijo.canchaId(), fijo.jugadores(), new Uso(fijo.inicio(), fijo.fin(), fijo.lugarId()));
        }

        // Cuántos huecos tiene cada partido sin contar a los demás: mide qué tan difícil es.
        Map<Long, Integer> holgura = new HashMap<>();
        for (PartidoPendiente partido : pendientes) {
            holgura.put(partido.id(), (int) candidatos(franjas, partido, desde).stream()
                    .filter(c -> respetaRestricciones(partido, c.inicio()))
                    .count());
        }
        List<PartidoPendiente> orden = new ArrayList<>(pendientes);
        orden.sort(Comparator.comparingInt(PartidoPendiente::prioridad)
                .thenComparingInt(p -> holgura.get(p.id()))
                .thenComparing(PartidoPendiente::id));

        Map<Long, String> sinLugar = new LinkedHashMap<>();
        for (PartidoPendiente partido : orden) {
            String[] motivo = new String[1];
            Candidato candidato = estado.mejor(partido, motivo);
            if (candidato != null) {
                estado.colocar(partido, candidato);
            } else {
                sinLugar.put(partido.id(), motivo[0]);
            }
        }

        for (int vuelta = 0; vuelta < VUELTAS_DE_REACOMODO && !sinLugar.isEmpty(); vuelta++) {
            boolean cambio = false;
            for (PartidoPendiente partido : orden) {
                if (sinLugar.containsKey(partido.id()) && reacomodar(estado, partido)) {
                    sinLugar.remove(partido.id());
                    cambio = true;
                }
            }
            if (!cambio) {
                break;
            }
        }

        List<Asignacion> asignaciones = new ArrayList<>(estado.colocados.values().stream()
                .map(c -> new Asignacion(c.partido().id(), c.candidato().franja().canchaId(), c.uso().inicio(), c.uso().fin()))
                .toList());
        asignaciones.sort(Comparator.comparing(Asignacion::inicio).thenComparing(Asignacion::canchaId));
        return new Resultado(asignaciones, sinLugar.entrySet().stream()
                .map(e -> new SinLugar(e.getKey(), e.getValue()))
                .toList());
    }

    /**
     * Le hace lugar a {@code trabado} sacando un partido ya ubicado que lo traba (comparte
     * un jugador, o tiene la cancha en un horario en que el trabado podría ir) y buscándole
     * otro hueco. Prueba con cada uno; si ninguno sirve, deja todo como estaba.
     */
    private static boolean reacomodar(Estado estado, PartidoPendiente trabado) {
        List<Candidato> huecos = candidatos(estado.franjas, trabado, estado.desde).stream()
                .filter(c -> respetaRestricciones(trabado, c.inicio()))
                .toList();
        if (huecos.isEmpty()) {
            return false;
        }
        List<Colocado> bloqueantes = estado.colocados.values().stream()
                .filter(c -> c.partido().jugadores().stream().anyMatch(trabado.jugadores()::contains)
                        || huecos.stream().anyMatch(h -> h.franja().canchaId().equals(c.candidato().franja().canchaId())
                                && h.inicio().isBefore(c.uso().fin())
                                && c.uso().inicio().isBefore(h.inicio().plusMinutes(trabado.duracionMinutos()))))
                .sorted(Comparator.comparing((Colocado c) -> c.uso().inicio()))
                .toList();

        String[] ignorado = new String[1];
        for (Colocado bloqueante : bloqueantes) {
            estado.sacar(bloqueante.partido().id());
            Candidato paraTrabado = estado.mejor(trabado, ignorado);
            if (paraTrabado != null) {
                estado.colocar(trabado, paraTrabado);
                Candidato paraBloqueante = estado.mejor(bloqueante.partido(), ignorado);
                if (paraBloqueante != null) {
                    estado.colocar(bloqueante.partido(), paraBloqueante);
                    return true;
                }
                estado.sacar(trabado.id());
            }
            estado.colocar(bloqueante.partido(), bloqueante.candidato());
        }
        return false;
    }

    /** Todos los arranques posibles del partido, de a {@link #PASO_MINUTOS}, dentro de las franjas que le sirven. */
    private static List<Candidato> candidatos(List<Franja> franjas, PartidoPendiente partido, LocalDateTime desde) {
        List<Candidato> candidatos = new ArrayList<>();
        for (Franja franja : franjas) {
            if (partido.lugarFijoId() != null && !partido.lugarFijoId().equals(franja.lugarId())) {
                continue;
            }
            LocalDateTime inicio = franja.inicio();
            if (inicio.isBefore(desde)) {
                inicio = redondearArriba(desde, franja.inicio());
            }
            while (!inicio.plusMinutes(partido.duracionMinutos()).isAfter(franja.fin())) {
                candidatos.add(new Candidato(franja, inicio));
                inicio = inicio.plusMinutes(PASO_MINUTOS);
            }
        }
        return candidatos;
    }

    /** El primer arranque en la grilla de la franja que no sea anterior a {@code desde}. */
    private static LocalDateTime redondearArriba(LocalDateTime desde, LocalDateTime origen) {
        long minutos = Duration.between(origen, desde).toMinutes();
        long pasos = (minutos + PASO_MINUTOS - 1) / PASO_MINUTOS;
        LocalDateTime inicio = origen.plusMinutes(pasos * PASO_MINUTOS);
        return inicio.isBefore(desde) ? inicio.plusMinutes(PASO_MINUTOS) : inicio;
    }

    private static boolean respetaRestricciones(PartidoPendiente partido, LocalDateTime inicio) {
        LocalDateTime fin = inicio.plusMinutes(partido.duracionMinutos());
        return partido.restricciones().stream().noneMatch(r -> r.pisa(inicio, fin));
    }

    /** Si el rato [inicio, fin) no se pisa con ningún uso, dejando {@code margen} minutos antes y después. */
    private static boolean libre(List<Uso> usos, LocalDateTime inicio, LocalDateTime fin, int margen) {
        if (usos == null) {
            return true;
        }
        LocalDateTime desde = inicio.minusMinutes(margen);
        LocalDateTime hasta = fin.plusMinutes(margen);
        for (Uso uso : usos) {
            if (desde.isBefore(uso.fin()) && uso.inicio().isBefore(hasta)) {
                return false;
            }
        }
        return true;
    }
}
