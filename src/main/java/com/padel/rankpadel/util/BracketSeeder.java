package com.padel.rankpadel.util;

import java.util.ArrayList;
import java.util.List;

import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.RondaEliminatorias;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.enums.FasePartido;

public final class BracketSeeder {

    private BracketSeeder() {
    }

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

    public static List<Match> sembrarPorPosicion(List<Pareja> clasificados) {
        long[] grupoIds = new long[clasificados.size()];
        for (int i = 0; i < clasificados.size(); i++) {
            Pareja pareja = clasificados.get(i);
            grupoIds[i] = (pareja.getGrupo() != null && pareja.getGrupo().getId() != null)
                    ? pareja.getGrupo().getId()
                    : 0L;
        }

        List<Match> matches = new ArrayList<>();
        for (int[] par : emparejarIndices(clasificados.size(), grupoIds)) {
            Pareja local = par[0] >= 0 ? clasificados.get(par[0]) : null;
            Pareja visitante = par[1] >= 0 ? clasificados.get(par[1]) : null;
            matches.add(new Match(local, visitante));
        }
        return matches;
    }

    public static List<int[]> emparejarIndices(int cantidad, long[] grupoIds) {
        int n = 1;
        while (n < cantidad) n *= 2;
        if (n < 2) n = 2;

        int[] orden = seedSlots(n);
        List<int[]> pares = new ArrayList<>();
        for (int i = 0; i + 1 < orden.length; i += 2) {
            int a = orden[i] - 1;
            int b = orden[i + 1] - 1;
            pares.add(new int[] { a < cantidad ? a : -1, b < cantidad ? b : -1 });
        }
        evitarMismoGrupoPorIndice(pares, grupoIds);
        return pares;
    }

    private static void evitarMismoGrupoPorIndice(List<int[]> pares, long[] grupoIds) {
        for (int i = 0; i < pares.size(); i++) {
            int[] par = pares.get(i);
            if (!mismoGrupoPorIndice(grupoIds, par[0], par[1])) continue;
            for (int j = 0; j < pares.size(); j++) {
                if (j == i) continue;
                int[] otro = pares.get(j);
                if (otro[1] < 0) continue;
                if (!mismoGrupoPorIndice(grupoIds, par[0], otro[1])
                        && !mismoGrupoPorIndice(grupoIds, otro[0], par[1])) {
                    int tmp = par[1];
                    par[1] = otro[1];
                    otro[1] = tmp;
                    break;
                }
            }
        }
    }

    private static boolean mismoGrupoPorIndice(long[] grupoIds, int x, int y) {
        if (x < 0 || y < 0) return false;
        long grupoX = grupoIds[x];
        return grupoX != 0 && grupoX == grupoIds[y];
    }

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

    public static int[] ordenDeSiembra(int n) {
        return seedSlots(n);
    }

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
