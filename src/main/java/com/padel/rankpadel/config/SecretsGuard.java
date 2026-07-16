package com.padel.rankpadel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Impide arrancar con la clave JWT de desarrollo (commiteada en el repo)
 * contra una base que no es local. Cubre el caso en que el contenedor
 * arranca sin SPRING_PROFILES_ACTIVE=prod y toma los defaults de dev.
 */
@Component
public class SecretsGuard {

    private static final String DEV_JWT_SECRET = "cmFua3BhZGVsLWRldi1rZXktMjAyNi1jaGFuZ2UtaW4tcHJvZHVjdGlvbiEh";

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @PostConstruct
    void verificar() {
        boolean baseLocal = datasourceUrl.contains("localhost") || datasourceUrl.contains("127.0.0.1");
        if (DEV_JWT_SECRET.equals(jwtSecret) && !baseLocal) {
            throw new IllegalStateException(
                    "La clave JWT de desarrollo no puede usarse contra una base remota. "
                            + "Definí JWT_SECRET (y SPRING_PROFILES_ACTIVE=prod) en el entorno.");
        }
    }
}
