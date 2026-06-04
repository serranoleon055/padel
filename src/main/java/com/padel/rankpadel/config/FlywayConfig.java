package com.padel.rankpadel.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ejecuta {@code flyway.repair()} antes de {@code migrate()} en cada arranque.
 *
 * <p>repair() limpia las entradas de migraciones fallidas (success=0) que quedan
 * en {@code flyway_schema_history} y realinea checksums. Es parte de la edicion
 * Community (gratuita) de Flyway, a diferencia de la propiedad
 * {@code spring.flyway.repair-on-migrate}, que solo existe en Flyway Teams y es
 * ignorada silenciosamente por la Community que usa Spring Boot.
 *
 * <p>Necesario para destrabar una base que quedo con una migracion en estado
 * failed (p. ej. la V16 que fallaba por un typo de columna).
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy repairThenMigrate() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
