package com.padel.rankpadel.util;

public final class NormalizadorRonda {

    private NormalizadorRonda() {
    }

    public static String normalizar(String nombreRonda) {
        if (nombreRonda == null) {
            return "";
        }

        String texto = NormalizadorTexto.normalizar(nombreRonda);

        if (texto.contains("grupo") || texto.contains("fecha") || texto.contains("jornada")) {
            return "GRUPOS";
        }
        if (texto.contains("treintaidosavo") || texto.contains("32avo") || texto.contains("ronda de 64")) {
            return "TREINTAIDOSAVOS";
        }
        if (texto.contains("dieciseisavo") || texto.contains("16avo") || texto.contains("ronda de 32")) {
            return "DIECISEISAVOS";
        }
        if (texto.contains("octavo") || texto.contains("ronda de 16")) {
            return "OCTAVOS";
        }
        if (texto.contains("cuarto") || texto.contains("ronda de 8")) {
            return "CUARTOS";
        }
        if (texto.contains("semi")) {
            return "SEMIFINAL";
        }
        if (texto.contains("final")) {
            return "FINAL";
        }

        return texto;
    }
}
