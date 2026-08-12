package com.padel.rankpadel.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lector de CSV para los archivos que salen de un Excel en español.
 *
 * <p>No usa una librería a propósito: el formato que llega es siempre el mismo —lo que
 * exporta el Excel del club— y las tres cosas que hay que contemplar son el separador
 * (Excel es-AR usa {@code ;}), el BOM que Windows le mete adelante, y las comillas
 * alrededor de los campos que tienen el separador adentro.
 *
 * <p>La cabecera se normaliza sin tildes ni mayúsculas: el club escribe "Teléfono",
 * "TELEFONO" o "telefono" y las tres tienen que entrar.
 */
public final class LectorCsv {

    private static final char BOM = '﻿';

    private LectorCsv() {
    }

    /**
     * Cada fila como un mapa columna → valor, con las columnas ya normalizadas.
     *
     * @throws IllegalArgumentException si el archivo está vacío o no tiene cabecera
     */
    public static List<Map<String, String>> leer(String contenido) {
        if (contenido == null || contenido.isBlank()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        String limpio = contenido.charAt(0) == BOM ? contenido.substring(1) : contenido;
        List<String> lineas = new ArrayList<>(List.of(limpio.split("\\r?\\n")));
        lineas.removeIf(String::isBlank);
        if (lineas.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }

        char separador = detectarSeparador(lineas.get(0));
        List<String> columnas = partir(lineas.get(0), separador).stream()
                .map(LectorCsv::normalizarColumna)
                .toList();

        List<Map<String, String>> filas = new ArrayList<>();
        for (int i = 1; i < lineas.size(); i++) {
            List<String> valores = partir(lineas.get(i), separador);
            Map<String, String> fila = new LinkedHashMap<>();
            for (int c = 0; c < columnas.size(); c++) {
                fila.put(columnas.get(c), c < valores.size() ? valores.get(c).trim() : "");
            }
            filas.add(fila);
        }
        return filas;
    }

    /**
     * Excel en español separa con {@code ;} y el resto del mundo con {@code ,}. Se elige
     * el que más aparezca en la cabecera, que es donde no hay datos que confundan.
     */
    private static char detectarSeparador(String cabecera) {
        long puntoYComa = cabecera.chars().filter(c -> c == ';').count();
        long coma = cabecera.chars().filter(c -> c == ',').count();
        return puntoYComa >= coma ? ';' : ',';
    }

    /** Respeta las comillas: un campo entrecomillado puede tener el separador adentro. */
    private static List<String> partir(String linea, char separador) {
        List<String> campos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean entreComillas = false;

        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (c == '"') {
                // Dos comillas seguidas adentro de un campo son una comilla literal.
                if (entreComillas && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    actual.append('"');
                    i++;
                } else {
                    entreComillas = !entreComillas;
                }
            } else if (c == separador && !entreComillas) {
                campos.add(actual.toString());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        campos.add(actual.toString());
        return campos;
    }

    private static String normalizarColumna(String columna) {
        return java.text.Normalizer.normalize(columna.trim().toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]", "");
    }

    /** El primer valor no vacío de las columnas dadas. Sirve para aceptar sinónimos. */
    public static String valor(Map<String, String> fila, String... columnas) {
        for (String columna : columnas) {
            String valor = fila.get(columna);
            if (valor != null && !valor.isBlank()) {
                return valor.trim();
            }
        }
        return null;
    }
}
