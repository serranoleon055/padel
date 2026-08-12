package com.padel.rankpadel.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LectorCsv - la planilla que exporta el club")
class LectorCsvTest {

    @Test
    @DisplayName("Lee el CSV que exporta un Excel en español, con punto y coma")
    void lee_separadorPuntoYComa() {
        List<Map<String, String>> filas = LectorCsv.leer("""
                nombre;apellido;telefono
                Juan;Perez;3856894061
                """);

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0)).containsEntry("nombre", "Juan").containsEntry("telefono", "3856894061");
    }

    @Test
    @DisplayName("También lee el CSV con comas")
    void lee_separadorComa() {
        List<Map<String, String>> filas = LectorCsv.leer("nombre,telefono\nJuan,3856894061");

        assertThat(filas.get(0)).containsEntry("nombre", "Juan").containsEntry("telefono", "3856894061");
    }

    @Test
    @DisplayName("Se saltea el BOM que Windows le mete adelante al archivo")
    void lee_ignoraElBom() {
        // Sin esto la primera columna se llama "﻿nombre" y no la encuentra nadie.
        List<Map<String, String>> filas = LectorCsv.leer("﻿nombre;telefono\nJuan;385");

        assertThat(filas.get(0)).containsKey("nombre");
    }

    @Test
    @DisplayName("Los títulos entran con tildes, mayúsculas y espacios")
    void lee_normalizaLaCabecera() {
        List<Map<String, String>> filas = LectorCsv.leer(" Teléfono ;NOMBRE\n385;Juan");

        assertThat(filas.get(0)).containsEntry("telefono", "385").containsEntry("nombre", "Juan");
    }

    @Test
    @DisplayName("Un campo entrecomillado puede tener el separador adentro")
    void lee_respetaLasComillas() {
        List<Map<String, String>> filas = LectorCsv.leer("nombre;notas\nJuan;\"Debe 2 turnos; avisar\"");

        assertThat(filas.get(0)).containsEntry("notas", "Debe 2 turnos; avisar");
    }

    @Test
    @DisplayName("Las filas con menos columnas que la cabecera no rompen")
    void lee_filaCorta() {
        List<Map<String, String>> filas = LectorCsv.leer("nombre;apellido;telefono\nJuan;Perez");

        assertThat(filas.get(0)).containsEntry("apellido", "Perez").containsEntry("telefono", "");
    }

    @Test
    @DisplayName("Un archivo vacío se rechaza con un mensaje, no con un índice fuera de rango")
    void lee_vacio_lanza() {
        assertThatThrownBy(() -> LectorCsv.leer("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vacío");
    }

    @Test
    @DisplayName("valor() acepta sinónimos de columna y devuelve el primero que tenga dato")
    void valor_aceptaSinonimos() {
        Map<String, String> fila = Map.of("telefono", "", "celular", "3856894061");

        assertThat(LectorCsv.valor(fila, "telefono", "celular")).isEqualTo("3856894061");
        assertThat(LectorCsv.valor(fila, "whatsapp")).isNull();
    }
}
