package com.padel.rankpadel.util;

import java.text.Normalizer;

public final class NormalizadorTexto {

    private NormalizadorTexto() {
    }

    public static String normalizarNombre(String nombre, String apellido) {
        String base = ((nombre == null ? "" : nombre) + " " + (apellido == null ? "" : apellido));
        return normalizar(base);
    }

    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinAcentos.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
