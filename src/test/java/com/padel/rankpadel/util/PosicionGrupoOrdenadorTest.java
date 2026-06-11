package com.padel.rankpadel.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.PosicionGrupo;

@DisplayName("PosicionGrupoOrdenador - desempate de grupos")
class PosicionGrupoOrdenadorTest {

    private PosicionGrupo posicion(long parejaId, int puntos, int setsGanados, int setsPerdidos,
            int juegosGanados, int juegosPerdidos) {
        return PosicionGrupo.builder()
                .pareja(Pareja.builder().id(parejaId).build())
                .puntos(puntos)
                .setsGanados(setsGanados)
                .setsPerdidos(setsPerdidos)
                .juegosGanados(juegosGanados)
                .juegosPerdidos(juegosPerdidos)
                .build();
    }

    private long idEnPosicion(List<PosicionGrupo> orden, int indice) {
        return orden.get(indice).getPareja().getId();
    }

    @Test
    @DisplayName("Ordena por puntos descendente")
    void ordenaPorPuntos() {
        List<PosicionGrupo> orden = PosicionGrupoOrdenador.ordenar(List.of(
                posicion(1L, 3, 2, 2, 12, 12),
                posicion(2L, 9, 6, 0, 18, 6),
                posicion(3L, 6, 4, 2, 15, 10)));

        assertEquals(2L, idEnPosicion(orden, 0));
        assertEquals(3L, idEnPosicion(orden, 1));
        assertEquals(1L, idEnPosicion(orden, 2));
    }

    @Test
    @DisplayName("Empate en puntos desempata por diferencia de sets")
    void desempataPorSets() {
        List<PosicionGrupo> orden = PosicionGrupoOrdenador.ordenar(List.of(
                posicion(1L, 6, 4, 3, 20, 18),
                posicion(2L, 6, 5, 1, 20, 18)));

        assertEquals(2L, idEnPosicion(orden, 0));
        assertEquals(1L, idEnPosicion(orden, 1));
    }

    @Test
    @DisplayName("Empate en puntos y sets desempata por diferencia de juegos")
    void desempataPorJuegos() {
        List<PosicionGrupo> orden = PosicionGrupoOrdenador.ordenar(List.of(
                posicion(1L, 6, 4, 2, 24, 20),
                posicion(2L, 6, 4, 2, 24, 16)));

        assertEquals(2L, idEnPosicion(orden, 0));
        assertEquals(1L, idEnPosicion(orden, 1));
    }

    @Test
    @DisplayName("Empate total entre tres parejas es determinístico por id y no lanza excepción")
    void empateTotalEsDeterministico() {
        List<PosicionGrupo> orden = PosicionGrupoOrdenador.ordenar(List.of(
                posicion(3L, 3, 2, 2, 12, 12),
                posicion(1L, 3, 2, 2, 12, 12),
                posicion(2L, 3, 2, 2, 12, 12)));

        assertEquals(1L, idEnPosicion(orden, 0));
        assertEquals(2L, idEnPosicion(orden, 1));
        assertEquals(3L, idEnPosicion(orden, 2));
    }
}
