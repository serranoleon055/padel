package com.padel.rankpadel.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.padel.rankpadel.entity.PosicionGrupo;

public final class PosicionGrupoOrdenador {

    private static final Comparator<PosicionGrupo> CRITERIO =
            Comparator.comparingInt(PosicionGrupo::getPuntos).reversed()
                    .thenComparing(Comparator.comparingInt(PosicionGrupoOrdenador::diferenciaSets).reversed())
                    .thenComparing(Comparator.comparingInt(PosicionGrupoOrdenador::diferenciaJuegos).reversed())
                    .thenComparingLong(PosicionGrupoOrdenador::idPareja);

    private PosicionGrupoOrdenador() {
    }

    public static List<PosicionGrupo> ordenar(List<PosicionGrupo> posiciones) {
        List<PosicionGrupo> ordenadas = new ArrayList<>(posiciones);
        ordenadas.sort(CRITERIO);
        return ordenadas;
    }

    private static int diferenciaSets(PosicionGrupo posicion) {
        return posicion.getSetsGanados() - posicion.getSetsPerdidos();
    }

    private static int diferenciaJuegos(PosicionGrupo posicion) {
        return posicion.getJuegosGanados() - posicion.getJuegosPerdidos();
    }

    private static long idPareja(PosicionGrupo posicion) {
        if (posicion.getPareja() == null || posicion.getPareja().getId() == null) {
            return Long.MAX_VALUE;
        }
        return posicion.getPareja().getId();
    }
}
