package com.padel.rankpadel.config;

import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class ConfiguracionZonaHoraria {

    @Value("${app.zona-horaria:America/Argentina/Buenos_Aires}")
    private String zonaHoraria;

    @PostConstruct
    public void aplicarZonaHoraria() {
        TimeZone.setDefault(TimeZone.getTimeZone(zonaHoraria));
    }
}
