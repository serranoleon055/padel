package com.padel.rankpadel.util;

import com.padel.rankpadel.enums.FormatoPartido;
import com.padel.rankpadel.exception.EstadoInvalidoException;

/**
 * Las reglas del marcador de pádel para cada {@link FormatoPartido}. El marcador viaja
 * como texto ("6-3 / 4-6 / 10-8"), siempre desde el lado del local.
 */
public final class MarcadorPadel {

    private static final String PATRON = "\\d+-\\d+(\\s*/\\s*\\d+-\\d+)*";
    private static final int PUNTOS_SUPER_TIEBREAK = 10;

    private MarcadorPadel() {
    }

    /**
     * Valida el marcador completo y dice quién ganó.
     *
     * @return true si ganó el local
     * @throws EstadoInvalidoException con un mensaje para el que lo está cargando
     */
    public static boolean ganoElLocal(String marcador, FormatoPartido formato) {
        if (marcador == null || !marcador.trim().matches(PATRON)) {
            throw new EstadoInvalidoException("El marcador debe tener formato 6-3 / 4-6 / 7-5");
        }

        int maximo = formato.getMaximoSets();
        int paraGanar = formato.getSetsParaGanar();
        String[] sets = separarSets(marcador);
        if (sets.length < paraGanar || sets.length > maximo) {
            throw new EstadoInvalidoException(maximo == 1
                    ? "Este partido es a un set: cargá un único resultado (por ejemplo "
                            + ejemploSetUnico(formato) + ")"
                    : "Un partido de pádel se define al mejor de " + maximo + " sets: cargá entre "
                            + paraGanar + " y " + maximo + " sets");
        }

        int setsLocal = 0;
        int setsVisitante = 0;
        for (int i = 0; i < sets.length; i++) {
            int[] games = leerSet(sets[i]);
            if (games[0] == games[1]) {
                throw new EstadoInvalidoException("Un set no puede terminar empatado");
            }
            validarSet(games[0], games[1], i + 1, formato);
            if (games[0] > games[1]) {
                setsLocal++;
            } else {
                setsVisitante++;
            }
            if ((setsLocal == paraGanar || setsVisitante == paraGanar) && i < sets.length - 1) {
                throw new EstadoInvalidoException("El partido ya quedó definido; no puede tener más sets");
            }
        }

        if (setsLocal != paraGanar && setsVisitante != paraGanar) {
            throw new EstadoInvalidoException("El partido no está definido: el ganador debe ganar " + paraGanar
                    + (paraGanar == 1 ? " set" : " sets"));
        }
        return setsLocal > setsVisitante;
    }

    /**
     * Games de cada lado, para la diferencia de games de la zona. El super tie-break se
     * cuenta como un game para el que lo ganó: sumar sus puntos (10-8) inflaría la
     * diferencia de quien llegó al tercer set frente a quien ganó en dos.
     */
    public static int[] contarGames(String marcador, FormatoPartido formato) {
        if (marcador == null || marcador.isBlank()) return new int[] { 0, 0 };
        String[] sets = separarSets(marcador);
        int local = 0;
        int visitante = 0;
        for (int i = 0; i < sets.length; i++) {
            int[] games;
            try {
                games = leerSet(sets[i]);
            } catch (RuntimeException e) {
                continue;
            }
            if (esSuperTiebreak(i, formato)) {
                if (games[0] > games[1]) local++;
                else if (games[1] > games[0]) visitante++;
            } else {
                local += games[0];
                visitante += games[1];
            }
        }
        return new int[] { local, visitante };
    }

    private static void validarSet(int local, int visitante, int numSet, FormatoPartido formato) {
        int ganador = Math.max(local, visitante);
        int perdedor = Math.min(local, visitante);

        if (esSuperTiebreak(numSet - 1, formato)) {
            boolean valido = ganador == PUNTOS_SUPER_TIEBREAK
                    ? perdedor <= PUNTOS_SUPER_TIEBREAK - 2
                    : ganador > PUNTOS_SUPER_TIEBREAK && ganador - perdedor == 2;
            if (!valido) {
                throw new EstadoInvalidoException("El super tie-break " + local + "-" + visitante
                        + " no es válido: se gana a 10 puntos con 2 de diferencia (10-8, 11-9, 12-10...)");
            }
            return;
        }

        if (formato.getGamesPorSet() == 9) {
            // Pro set: a 9 games, con tie-break en 8-8. 9-8 vale; 10-8 no existe.
            if (ganador != 9) {
                throw new EstadoInvalidoException("El set a 9 games " + local + "-" + visitante
                        + " no es válido: se gana con 9 games (9-8 si hubo tie-break)");
            }
            return;
        }

        if (ganador < 6) {
            throw new EstadoInvalidoException("El set " + numSet + " no tiene un marcador válido (mínimo 6 juegos): " + local + "-" + visitante);
        }
        if (ganador == 6 && ganador - perdedor < 2) {
            throw new EstadoInvalidoException("El set " + numSet + " con " + local + "-" + visitante + " no es válido: se necesita ventaja de 2 o llegar a 7");
        }
        if (ganador == 7 && perdedor < 5) {
            throw new EstadoInvalidoException("El set " + numSet + " con " + local + "-" + visitante + " no es un marcador válido de pádel");
        }
        if (ganador > 7) {
            throw new EstadoInvalidoException("Un set no puede superar 7 juegos: " + local + "-" + visitante);
        }
    }

    private static boolean esSuperTiebreak(int indiceSet, FormatoPartido formato) {
        return formato.isSuperTiebreakDecisivo() && indiceSet == formato.getMaximoSets() - 1;
    }

    private static String[] separarSets(String marcador) {
        return marcador.trim().split("\\s*/\\s*");
    }

    private static int[] leerSet(String set) {
        String[] games = set.trim().split("-");
        return new int[] { Integer.parseInt(games[0].trim()), Integer.parseInt(games[1].trim()) };
    }

    private static String ejemploSetUnico(FormatoPartido formato) {
        return formato.getGamesPorSet() == 9 ? "9-5" : "6-3";
    }
}
