package com.padel.rankpadel.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OrdenCanchas - las canchas se ordenan por número")
class OrdenCanchasTest {

    private List<String> ordenar(String... nombres) {
        List<String> lista = new ArrayList<>(Arrays.asList(nombres));
        lista.sort(OrdenCanchas.porNombre(nombre -> nombre));
        return lista;
    }

    @Test
    @DisplayName("la 10 va después de la 2, no antes (que es lo que hace el orden alfabético)")
    void ordenaPorNumeroYNoPorTexto() {
        assertEquals(List.of("Cancha 1", "Cancha 2", "Cancha 10", "Cancha 11"),
                ordenar("Cancha 10", "Cancha 2", "Cancha 11", "Cancha 1"));
    }

    @Test
    @DisplayName("el número manda aunque el nombre cambie")
    void ordenaAunqueElNombreNoSeaUniforme() {
        assertEquals(List.of("Cancha 1", "Blindex 2", "Cancha 3"),
                ordenar("Cancha 3", "Cancha 1", "Blindex 2"));
    }

    @Test
    @DisplayName("las canchas sin número van al final, alfabéticas")
    void lasSinNumeroVanAlFinal() {
        assertEquals(List.of("Cancha 2", "Central", "Techada"),
                ordenar("Techada", "Central", "Cancha 2"));
    }

    @Test
    @DisplayName("un nombre nulo no rompe el orden")
    void toleraNulos() {
        List<String> lista = new ArrayList<>(Arrays.asList("Cancha 2", null, "Cancha 1"));
        lista.sort(OrdenCanchas.porNombre(nombre -> nombre));
        assertEquals(Arrays.asList("Cancha 1", "Cancha 2", null), lista);
    }

}
