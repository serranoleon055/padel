package com.padel.rankpadel.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BracketSeeder - emparejado por posición de grupo")
class BracketSeederTest {

    @Test
    @DisplayName("4 grupos, 2 avanzan: cruces fijos 1°A-2°D, 1°D-2°A, 1°B-2°C, 1°C-2°B")
    void cuatroGrupos_dosAvanzan_crucesFijos() {
        long[] grupoIds = { 1, 2, 3, 4, 1, 2, 3, 4 };

        List<int[]> pares = BracketSeeder.emparejarIndices(8, grupoIds);

        assertArrayEquals(new int[] { 0, 7 }, pares.get(0));
        assertArrayEquals(new int[] { 3, 4 }, pares.get(1));
        assertArrayEquals(new int[] { 1, 6 }, pares.get(2));
        assertArrayEquals(new int[] { 2, 5 }, pares.get(3));
    }

    @Test
    @DisplayName("3 grupos, 2 avanzan: nunca cruza dos parejas del mismo grupo en primera ronda")
    void tresGrupos_evitaMismoGrupoEnPrimeraRonda() {
        long[] grupoIds = { 1, 2, 3, 1, 2, 3 };

        List<int[]> pares = BracketSeeder.emparejarIndices(6, grupoIds);

        for (int[] par : pares) {
            if (par[0] >= 0 && par[1] >= 0) {
                assertNotEquals(grupoIds[par[0]], grupoIds[par[1]],
                        "no debe haber dos parejas del mismo grupo en la primera ronda");
            }
        }
    }

    @Test
    @DisplayName("2 grupos, 2 avanzan: 1°A-2°B y 1°B-2°A")
    void dosGrupos_dosAvanzan_crucesCruzados() {
        long[] grupoIds = { 1, 2, 1, 2 };

        List<int[]> pares = BracketSeeder.emparejarIndices(4, grupoIds);

        assertArrayEquals(new int[] { 0, 3 }, pares.get(0));
        assertArrayEquals(new int[] { 1, 2 }, pares.get(1));
    }
}
