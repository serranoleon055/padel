package com.padel.rankpadel.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

        private static final String SECURITY_SCHEME_NAME = "bearerAuth";

        @Bean
        OpenAPI rankPadelOpenAPI() {
                return new OpenAPI()
                                .info(new Info()
                                                .title("RankPadel API")
                                                .version("1.0.0")
                                                .description(
                                                                """
                                                                                API REST para la gestión de torneos de pádel en Santiago del Estero, Argentina.

                                                                                Permite administrar jugadores, categorías, temporadas, torneos, inscripciones,
                                                                                sorteos, carga de resultados y consulta del ranking.

                                                                                **Endpoints públicos:** GET de jugadores, categorías, torneos, lugares, temporadas y ranking.

                                                                                **Endpoints protegidos:** todos los POST, PUT, PATCH y DELETE requieren autenticación JWT.
                                                                                Para autenticarte, usá el endpoint `/auth/login` y luego el botón **Authorize** con el token obtenido.
                                                                                """)
                                                .contact(new Contact()
                                                                .name("RankPadel")
                                                                .email("admin@rankpadel.com")))

                                .components(new Components()
                                                .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                                                .name(SECURITY_SCHEME_NAME)
                                                                .type(SecurityScheme.Type.HTTP)
                                                                .scheme("bearer")
                                                                .bearerFormat("JWT")
                                                                .description(
                                                                                "Ingresá el token JWT obtenido desde /auth/login. Incluí el prefijo 'Bearer ' (con espacio al final).")))

                                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
        }
}
