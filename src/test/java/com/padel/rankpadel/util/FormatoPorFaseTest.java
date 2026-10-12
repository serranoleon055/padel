package com.padel.rankpadel.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.padel.rankpadel.entity.ConfiguracionCategoriaTorneo;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.RondaEliminatorias;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.FasePartido;
import com.padel.rankpadel.enums.FormatoPartido;

@DisplayName("FormatoPorFase - con qué formato se juega cada partido")
class FormatoPorFaseTest {

    private final Torneo torneo = Torneo.builder().id(1L).mejorDeSets(3).build();

    @Test
    @DisplayName("Torneo viejo sin configuración: sale de mejorDeSets del torneo")
    void sinConfig() {
        assertThat(FormatoPorFase.resolver(partido(FasePartido.GRUPOS, null), null)).isEqualTo(FormatoPartido.TRES_SETS);
        Torneo unSet = Torneo.builder().id(2L).mejorDeSets(1).build();
        Partido partido = Partido.builder().torneo(unSet).fase(FasePartido.GRUPOS).build();
        assertThat(FormatoPorFase.resolver(partido, null)).isEqualTo(FormatoPartido.UN_SET);
    }

    @Test
    @DisplayName("Zona a un set, llave a dos sets con super tie-break, finales a 9 games")
    void porFase() {
        ConfiguracionCategoriaTorneo config = ConfiguracionCategoriaTorneo.builder()
                .mejorDeSets(3)
                .formatoPartidoGrupos(FormatoPartido.UN_SET)
                .formatoPartidoEliminacion(FormatoPartido.DOS_SETS_SUPER_TIEBREAK)
                .formatoPartidoFinales(FormatoPartido.PRO_SET_9)
                .build();

        assertThat(FormatoPorFase.resolver(partido(FasePartido.GRUPOS, null), config)).isEqualTo(FormatoPartido.UN_SET);
        assertThat(FormatoPorFase.resolver(partido(FasePartido.ELIMINACION, "Cuartos de final"), config))
                .isEqualTo(FormatoPartido.DOS_SETS_SUPER_TIEBREAK);
        assertThat(FormatoPorFase.resolver(partido(FasePartido.ELIMINACION, "Semifinales"), config))
                .isEqualTo(FormatoPartido.PRO_SET_9);
        assertThat(FormatoPorFase.resolver(partido(FasePartido.ELIMINACION, "Final"), config))
                .isEqualTo(FormatoPartido.PRO_SET_9);
    }

    @Test
    @DisplayName("Lo que no se configura hereda: finales de la llave, llave del general")
    void herencia() {
        ConfiguracionCategoriaTorneo soloLlave = ConfiguracionCategoriaTorneo.builder()
                .mejorDeSets(1)
                .formatoPartidoEliminacion(FormatoPartido.PRO_SET_9)
                .build();

        assertThat(FormatoPorFase.resolver(partido(FasePartido.GRUPOS, null), soloLlave)).isEqualTo(FormatoPartido.UN_SET);
        assertThat(FormatoPorFase.resolver(partido(FasePartido.ELIMINACION, "Final"), soloLlave)).isEqualTo(FormatoPartido.PRO_SET_9);

        ConfiguracionCategoriaTorneo nada = ConfiguracionCategoriaTorneo.builder().mejorDeSets(3).build();
        assertThat(FormatoPorFase.resolver(partido(FasePartido.ELIMINACION, "Final"), nada)).isEqualTo(FormatoPartido.TRES_SETS);
    }

    private Partido partido(FasePartido fase, String ronda) {
        return Partido.builder()
                .torneo(torneo)
                .fase(fase)
                .ronda(ronda == null ? null : RondaEliminatorias.builder().nombre(ronda).build())
                .build();
    }
}
