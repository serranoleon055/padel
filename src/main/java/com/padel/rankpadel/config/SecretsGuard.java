package com.padel.rankpadel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Impide arrancar con configuración de desarrollo contra una base que no es local:
 * la clave JWT commiteada en el repo y el modo demo de pagos. Cubre el caso en que
 * el contenedor arranca sin SPRING_PROFILES_ACTIVE=prod y toma los defaults de dev.
 */
@Component
public class SecretsGuard {

    private static final String DEV_JWT_SECRET = "cmFua3BhZGVsLWRldi1rZXktMjAyNi1jaGFuZ2UtaW4tcHJvZHVjdGlvbiEh";

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${app.pagos.modo-demo:false}")
    private boolean modoDemo;

    @Value("${app.pagos.demo-publica:false}")
    private boolean demoPublica;

    @PostConstruct
    void verificar() {
        boolean baseLocal = datasourceUrl.contains("localhost") || datasourceUrl.contains("127.0.0.1");
        if (DEV_JWT_SECRET.equals(jwtSecret) && !baseLocal) {
            throw new IllegalStateException(
                    "La clave JWT de desarrollo no puede usarse contra una base remota. "
                            + "Definí JWT_SECRET (y SPRING_PROFILES_ACTIVE=prod) en el entorno.");
        }
        // En modo demo los pagos se aprueban solos: contra la base de un club real,
        // estaría confirmando turnos e inscripciones sin cobrar un peso. La instancia
        // de demostración lo declara aparte, para que un PAGOS_MODO_DEMO olvidado al
        // dar de alta un cliente rompa el arranque en vez de pasar desapercibido.
        if (modoDemo && !baseLocal && !demoPublica) {
            throw new IllegalStateException(
                    "PAGOS_MODO_DEMO=true confirma turnos sin cobrar y esta base no es local. "
                            + "Si es un cliente real poné PAGOS_MODO_DEMO=false; si es la instancia "
                            + "de demostración, declaralo con PAGOS_DEMO_PUBLICA=true.");
        }
    }
}
