package com.padel.rankpadel.util;

import java.util.ArrayList;
import java.util.List;

import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.PosicionGrupo;

/**
 * Ordena la tabla de posiciones de un grupo según los criterios de desempate del torneo:
 * <ol>
 *   <li>Puntos (descendente)</li>
 *   <li>Diferencia de sets (descendente)</li>
 *   <li>Resultado directo (head-to-head) entre las parejas empatadas</li>
 *   <li>Diferencia de juegos (descendente)</li>
 * </ol>
 * El head-to-head se resuelve mirando el/los partido(s) de grupo jugados entre las dos parejas.
 */
public final class PosicionGrupoOrdenador {

    private PosicionGrupoOrdenador() {
    }

    /** Devuelve una copia ordenada de las posiciones aplicando los criterios de desempate. */
    public static List<PosicionGrupo> ordenar(List<PosicionGrupo> posiciones, List<Partido> partidosGrupo) {
        List<PosicionGrupo> lista = new ArrayList<>(posiciones);
        lista.sort((a, b) -> comparar(a, b, partidosGrupo));
        return lista;
    }

    private static int comparar(PosicionGrupo a, PosicionGrupo b, List<Partido> partidos) {
        // 1. Puntos DESC
        int cmp = Integer.compare(b.getPuntos(), a.getPuntos());
        if (cmp != 0) return cmp;

        // 2. Diferencia de sets DESC
        int difSetsA = a.getSetsGanados() - a.getSetsPerdidos();
        int difSetsB = b.getSetsGanados() - b.getSetsPerdidos();
        cmp = Integer.compare(difSetsB, difSetsA);
        if (cmp != 0) return cmp;

        // 3. Resultado directo entre las empatadas
        int h2h = headToHead(a, b, partidos);
        if (h2h != 0) return h2h;

        // 4. Diferencia de juegos DESC
        int difJuegosA = a.getJuegosGanados() - a.getJuegosPerdidos();
        int difJuegosB = b.getJuegosGanados() - b.getJuegosPerdidos();
        cmp = Integer.compare(difJuegosB, difJuegosA);
        if (cmp != 0) return cmp;

        // 5. Fallback determinístico para no dejar el orden al azar
        cmp = Integer.compare(b.getSetsGanados(), a.getSetsGanados());
        if (cmp != 0) return cmp;
        Long idA = a.getPareja() != null ? a.getPareja().getId() : 0L;
        Long idB = b.getPareja() != null ? b.getPareja().getId() : 0L;
        return Long.compare(idA, idB);
    }

    /**
     * Negativo si A le ganó a B (A va primero), positivo si B le ganó a A, 0 si no hay
     * un resultado directo registrado entre ambas.
     */
    private static int headToHead(PosicionGrupo a, PosicionGrupo b, List<Partido> partidos) {
        if (a.getPareja() == null || b.getPareja() == null) return 0;
        Long idA = a.getPareja().getId();
        Long idB = b.getPareja().getId();
        for (Partido p : partidos) {
            if (p.getGanador() == null || p.getLocal() == null || p.getVisitante() == null) continue;
            boolean involucra =
                    (p.getLocal().getId().equals(idA) && p.getVisitante().getId().equals(idB)) ||
                    (p.getLocal().getId().equals(idB) && p.getVisitante().getId().equals(idA));
            if (!involucra) continue;
            if (p.getGanador().getId().equals(idA)) return -1;
            if (p.getGanador().getId().equals(idB)) return 1;
        }
        return 0;
    }
}
