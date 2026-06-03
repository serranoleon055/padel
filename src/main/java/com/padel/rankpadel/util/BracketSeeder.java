package com.padel.rankpadel.util;

import java.util.ArrayList;
import java.util.List;

import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.RondaEliminatorias;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.enums.FasePartido;

/**
 * Genera el cuadro de eliminación con siembra estándar a partir de una lista de parejas ya
 * ordenada de mejor (índice 0) a peor.
 *
 * <p>Propiedades garantizadas:
 * <ul>
 *   <li>El sembrado clásico ubica a los mejores seeds en mitades/cuartos opuestos del cuadro
 *       (seed 1 y seed 2 sólo pueden cruzarse en la final).</li>
 *   <li>Cuando los primeros seeds son ganadores de grupo y los últimos son segundos, cada partido
 *       de 1ª ronda enfrenta a un ganador contra un segundo.</li>
 *   <li>Se evita, cuando es posible, que dos parejas del mismo grupo se crucen en la 1ª ronda.</li>
 *   <li>Los BYEs (cuando la cantidad no es potencia de 2) quedan para los mejores seeds.</li>
 * </ul>
 *
 * <p>El orden de los partidos devueltos es el orden de la llave: emparejando ganadores
 * consecutivos (0 vs 1, 2 vs 3, …) en cada ronda se preserva siempre el mismo lado del cuadro.
 */
public final class BracketSeeder {

    private BracketSeeder() {
    }

    /** Un partido de la primera ronda. {@code visitante == null} indica un BYE para {@code local}. */
    public static final class Match {
        public Pareja local;
        public Pareja visitante;

        Match(Pareja local, Pareja visitante) {
            this.local = local;
            this.visitante = visitante;
        }
    }

    public static List<Match> sembrar(List<Pareja> seeds) {
        int n = 1;
        while (n < seeds.size()) n *= 2;
        if (n < 2) n = 2;

        List<Pareja> padded = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            padded.add(i < seeds.size() ? seeds.get(i) : null);
        }

        int[] order = seedSlots(n);
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i + 1 < order.length; i += 2) {
            Pareja a = padded.get(order[i] - 1);
            Pareja b = padded.get(order[i + 1] - 1);
            matches.add(new Match(a, b));
        }

        evitarMismoGrupo(matches);
        return matches;
    }

    /**
     * Construye los {@link Partido} de la primera ronda a partir de la llave sembrada, en orden de
     * cuadro ({@code ordenLlave} 0,1,2,…). Los partidos con un solo lado quedan como BYE.
     */
    public static List<Partido> construirPartidos(List<Match> llave, Torneo torneo, RondaEliminatorias ronda) {
        List<Partido> partidos = new ArrayList<>();
        int orden = 0;
        for (Match m : llave) {
            Pareja local = m.local != null ? m.local : m.visitante;
            Pareja visitante = m.local != null ? m.visitante : null;
            Partido.PartidoBuilder b = Partido.builder()
                    .torneo(torneo)
                    .ronda(ronda)
                    .fase(FasePartido.ELIMINACION)
                    .ordenLlave(orden++)
                    .local(local)
                    .visitante(visitante);
            if (local != null && visitante == null) {
                b.estado(EstadoPartido.BYE).ganador(local);
            } else {
                b.estado(EstadoPartido.PENDIENTE);
            }
            partidos.add(b.build());
        }
        return partidos;
    }

    /**
     * Posiciones de siembra estándar para un cuadro de tamaño {@code n} (potencia de 2).
     * Ej. n=8 → [1,8,4,5,2,7,3,6]; los pares consecutivos son los cruces de 1ª ronda.
     */
    private static int[] seedSlots(int n) {
        List<Integer> order = new ArrayList<>();
        order.add(1);
        int size = 1;
        while (size < n) {
            int newSize = size * 2;
            List<Integer> next = new ArrayList<>();
            for (int s : order) {
                next.add(s);
                next.add(newSize + 1 - s);
            }
            order = next;
            size = newSize;
        }
        return order.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Intenta deshacer cruces de 1ª ronda entre parejas del mismo grupo intercambiando visitantes. */
    private static void evitarMismoGrupo(List<Match> matches) {
        for (int i = 0; i < matches.size(); i++) {
            Match m = matches.get(i);
            if (m.local == null || m.visitante == null || !mismoGrupo(m.local, m.visitante)) continue;

            for (int j = 0; j < matches.size(); j++) {
                if (j == i) continue;
                Match o = matches.get(j);
                if (o.visitante == null) continue;
                if (!mismoGrupo(m.local, o.visitante) && !mismoGrupo(o.local, m.visitante)) {
                    Pareja tmp = m.visitante;
                    m.visitante = o.visitante;
                    o.visitante = tmp;
                    break;
                }
            }
        }
    }

    private static boolean mismoGrupo(Pareja a, Pareja b) {
        if (a == null || b == null || a.getGrupo() == null || b.getGrupo() == null) return false;
        return a.getGrupo().getId() != null && a.getGrupo().getId().equals(b.getGrupo().getId());
    }
}
