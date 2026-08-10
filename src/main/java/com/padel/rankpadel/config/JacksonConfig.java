package com.padel.rankpadel.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Definir un {@code ObjectMapper} propio hace que Spring Boot se corra al costado: las
 * properties {@code spring.jackson.*} dejan de aplicarse. Por eso lo que este mapper
 * necesita va acá y no en el .properties.
 *
 * <p>Nota: los nulos SÍ se serializan (no se aplica {@code default-property-inclusion}),
 * para que el frontend distinga "campo sin valor" de "campo que no vino".
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Un campo de más en el body es un error del cliente, no del servidor: sin esto
        // Jackson tiraba una excepción que terminaba en 500 y ensuciaba el error tracking.
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}