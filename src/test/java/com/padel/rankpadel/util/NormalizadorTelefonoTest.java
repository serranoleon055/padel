package com.padel.rankpadel.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("NormalizadorTelefono")
class NormalizadorTelefonoTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "3856894061,      3856894061",
            "385 689 4061,    3856894061",
            "385-689-4061,    3856894061",
            "(385) 689 4061,  3856894061",
            "03856894061,     3856894061",
            "+54 385 689 4061,3856894061",
            "+54 9 385 689 4061, 3856894061",
            "54 9 385 6894061,3856894061",
            "0385 15 689 4061,3856894061",
            "385 15 6894061,  3856894061",
    })
    @DisplayName("Reconoce a la misma persona escriba como escriba el número")
    void normaliza_variantesDelMismoNumero(String entrada, String esperado) {
        assertThat(NormalizadorTelefono.normalizar(entrada)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("No mutila un número de Buenos Aires que empieza con 15 después del área")
    void normaliza_noRompeNumeroDeBuenosAires() {
        // 11 5689 4061 ya mide 10 dígitos: ese "15" es parte del abonado, no un prefijo.
        assertThat(NormalizadorTelefono.normalizar("11 5689 4061")).isEqualTo("1156894061");
        assertThat(NormalizadorTelefono.normalizar("+54 9 11 5689 4061")).isEqualTo("1156894061");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "abc", "123", "1234567" })
    @DisplayName("Descarta lo que no puede ser un teléfono")
    void normaliza_descartaBasura(String entrada) {
        assertThat(NormalizadorTelefono.normalizar(entrada)).isNull();
    }

    @Test
    @DisplayName("Un null entra y un null sale")
    void normaliza_null() {
        assertThat(NormalizadorTelefono.normalizar(null)).isNull();
    }
}
