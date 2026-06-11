package com.padel.rankpadel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.TimeZone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("ConfiguracionZonaHoraria")
class ConfiguracionZonaHorariaTest {

    @Test
    @DisplayName("Fija la zona horaria del proceso a la configurada")
    void aplicaLaZonaHorariaConfigurada() {
        TimeZone original = TimeZone.getDefault();
        try {
            ConfiguracionZonaHoraria configuracion = new ConfiguracionZonaHoraria();
            ReflectionTestUtils.setField(configuracion, "zonaHoraria", "America/Argentina/Buenos_Aires");

            configuracion.aplicarZonaHoraria();

            assertEquals("America/Argentina/Buenos_Aires", TimeZone.getDefault().getID());
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
