package com.padel.rankpadel.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.padel.rankpadel.enums.FormatoPartido;
import com.padel.rankpadel.exception.EstadoInvalidoException;

@DisplayName("MarcadorPadel - reglas de cada formato de partido")
class MarcadorPadelTest {

    @Nested
    @DisplayName("Tres sets")
    class TresSets {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = { "6-3 / 6-4", "6-3 / 3-6 / 7-5", "7-6 / 6-7 / 6-0", "6-4/6-4" })
        void validos(String marcador) {
            MarcadorPadel.ganoElLocal(marcador, FormatoPartido.TRES_SETS);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = { "6-3", "6-1 / 6-2 / 6-3", "6-3 / 3-6", "6-5 / 6-0", "8-6 / 6-0", "6-6 / 6-0", "seis-tres" })
        void invalidos(String marcador) {
            assertThatThrownBy(() -> MarcadorPadel.ganoElLocal(marcador, FormatoPartido.TRES_SETS))
                    .isInstanceOf(EstadoInvalidoException.class);
        }

        @Test
        void diceQuienGano() {
            assertThat(MarcadorPadel.ganoElLocal("6-3 / 3-6 / 7-5", FormatoPartido.TRES_SETS)).isTrue();
            assertThat(MarcadorPadel.ganoElLocal("3-6 / 6-3 / 5-7", FormatoPartido.TRES_SETS)).isFalse();
        }
    }

    @Nested
    @DisplayName("Dos sets y super tie-break")
    class SuperTiebreak {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = { "6-3 / 6-4", "6-3 / 3-6 / 10-8", "6-3 / 3-6 / 12-10", "4-6 / 6-4 / 3-10" })
        void validos(String marcador) {
            MarcadorPadel.ganoElLocal(marcador, FormatoPartido.DOS_SETS_SUPER_TIEBREAK);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = { "6-3 / 3-6 / 10-9", "6-3 / 3-6 / 7-5", "6-3 / 3-6 / 12-9", "6-3 / 3-6 / 9-7" })
        void invalidos(String marcador) {
            assertThatThrownBy(() -> MarcadorPadel.ganoElLocal(marcador, FormatoPartido.DOS_SETS_SUPER_TIEBREAK))
                    .isInstanceOf(EstadoInvalidoException.class);
        }

        @Test
        @DisplayName("El super tie-break vale un game para la diferencia de la zona, no sus puntos")
        void contarGames() {
            assertThat(MarcadorPadel.contarGames("6-3 / 3-6 / 10-8", FormatoPartido.DOS_SETS_SUPER_TIEBREAK))
                    .containsExactly(10, 9);
            assertThat(MarcadorPadel.contarGames("6-3 / 3-6 / 7-5", FormatoPartido.TRES_SETS))
                    .containsExactly(16, 14);
        }
    }

    @Nested
    @DisplayName("Un set")
    class UnSet {

        @Test
        void unSetDefine() {
            assertThat(MarcadorPadel.ganoElLocal("6-3", FormatoPartido.UN_SET)).isTrue();
        }

        @Test
        void dosSetsSobran() {
            assertThatThrownBy(() -> MarcadorPadel.ganoElLocal("6-3 / 6-4", FormatoPartido.UN_SET))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("un set");
        }
    }

    @Nested
    @DisplayName("Pro set a 9")
    class ProSet {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = { "9-0", "9-7", "9-8", "8-9", "5-9" })
        void validos(String marcador) {
            MarcadorPadel.ganoElLocal(marcador, FormatoPartido.PRO_SET_9);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = { "6-3", "10-8", "8-7", "9-9", "9-3 / 9-4" })
        void invalidos(String marcador) {
            assertThatThrownBy(() -> MarcadorPadel.ganoElLocal(marcador, FormatoPartido.PRO_SET_9))
                    .isInstanceOf(EstadoInvalidoException.class);
        }
    }
}
