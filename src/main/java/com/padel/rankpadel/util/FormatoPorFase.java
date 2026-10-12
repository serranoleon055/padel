package com.padel.rankpadel.util;

import com.padel.rankpadel.entity.ConfiguracionCategoriaTorneo;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.enums.FasePartido;
import com.padel.rankpadel.enums.FormatoPartido;

/**
 * Con qué formato se juega un partido puntual, según su categoría y su fase.
 *
 * <p>Lo que no se configuró hereda: las finales (semifinal y final) toman el de la
 * llave, y la llave el general de la categoría. Un torneo creado antes de los formatos
 * por fase solo tiene "al mejor de N sets", y se sigue leyendo igual que siempre.
 */
public final class FormatoPorFase {

    private FormatoPorFase() {
    }

    /**
     * @param config la configuración de la categoría del partido; null si el torneo no
     *               tiene (torneos viejos de una sola configuración)
     */
    public static FormatoPartido resolver(Partido partido, ConfiguracionCategoriaTorneo config) {
        Integer mejorDeSets = config != null && config.getMejorDeSets() != null
                ? config.getMejorDeSets()
                : partido.getTorneo() != null ? partido.getTorneo().getMejorDeSets() : null;
        FormatoPartido general = FormatoPartido.desdeMejorDeSets(mejorDeSets);
        if (config == null) {
            return general;
        }

        if (partido.getFase() == FasePartido.GRUPOS) {
            return primeroNoNulo(config.getFormatoPartidoGrupos(), general);
        }
        FormatoPartido llave = primeroNoNulo(config.getFormatoPartidoEliminacion(), general);
        return esSemifinalOFinal(partido) ? primeroNoNulo(config.getFormatoPartidoFinales(), llave) : llave;
    }

    private static boolean esSemifinalOFinal(Partido partido) {
        if (partido.getRonda() == null) {
            return false;
        }
        String ronda = NormalizadorRonda.normalizar(partido.getRonda().getNombre());
        return "SEMIFINAL".equals(ronda) || "FINAL".equals(ronda);
    }

    private static FormatoPartido primeroNoNulo(FormatoPartido valor, FormatoPartido siNo) {
        return valor != null ? valor : siNo;
    }
}
