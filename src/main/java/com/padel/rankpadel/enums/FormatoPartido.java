package com.padel.rankpadel.enums;

/**
 * Cómo se juega un partido. Se elige por categoría y por fase: es común jugar la zona a
 * un set y las semifinales y la final a 9 games, o la llave a dos sets con super
 * tie-break para que entre en la grilla del sábado.
 *
 * <p>Las reglas de cada uno viven en {@link com.padel.rankpadel.util.MarcadorPadel}.
 */
public enum FormatoPartido {

    /** Al mejor de 3 sets a 6 games, el tercero completo. */
    TRES_SETS(3, 6, false, 90),

    /** Al mejor de 3, pero el tercero se define con un super tie-break a 10 puntos. */
    DOS_SETS_SUPER_TIEBREAK(3, 6, true, 75),

    /** Un único set a 6 games. */
    UN_SET(1, 6, false, 45),

    /** Un único set a 9 games, con tie-break en 8-8 (termina 9-8). */
    PRO_SET_9(1, 9, false, 60);

    private final int maximoSets;
    private final int gamesPorSet;
    private final boolean superTiebreakDecisivo;
    private final int duracionSugeridaMinutos;

    FormatoPartido(int maximoSets, int gamesPorSet, boolean superTiebreakDecisivo, int duracionSugeridaMinutos) {
        this.maximoSets = maximoSets;
        this.gamesPorSet = gamesPorSet;
        this.superTiebreakDecisivo = superTiebreakDecisivo;
        this.duracionSugeridaMinutos = duracionSugeridaMinutos;
    }

    /**
     * Cuánto turno de cancha se le reserva a un partido así si la categoría no dice otra
     * cosa. Es lo que usan los organizadores como regla general; cada categoría lo puede
     * cambiar en las reglas del fixture.
     */
    public int getDuracionSugeridaMinutos() {
        return duracionSugeridaMinutos;
    }

    public int getMaximoSets() {
        return maximoSets;
    }

    public int getSetsParaGanar() {
        return maximoSets / 2 + 1;
    }

    public int getGamesPorSet() {
        return gamesPorSet;
    }

    public boolean isSuperTiebreakDecisivo() {
        return superTiebreakDecisivo;
    }

    /**
     * Lo que equivale a la configuración vieja "al mejor de N sets", que es lo único que
     * tienen guardado los torneos anteriores a los formatos por fase.
     */
    public static FormatoPartido desdeMejorDeSets(Integer mejorDeSets) {
        return mejorDeSets != null && mejorDeSets == 1 ? UN_SET : TRES_SETS;
    }
}
