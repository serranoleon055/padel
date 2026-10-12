package com.padel.rankpadel.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PesosTest {

    @Test
    void formatoArgentino() {
        assertThat(Pesos.formatear(new BigDecimal("15000.00"))).isEqualTo("$15.000");
        assertThat(Pesos.formatear(new BigDecimal("7500.5"))).isEqualTo("$7.500,50");
        assertThat(Pesos.formatear(new BigDecimal("1250000"))).isEqualTo("$1.250.000");
        assertThat(Pesos.formatear(null)).isEqualTo("$0");
    }
}
