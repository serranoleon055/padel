package com.padel.rankpadel.exception;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Estructura estándar de error devuelta por la API ante cualquier falla")
public class ApiError {

    @Schema(description = "Código HTTP del error", example = "404")
    private int status;

    @Schema(description = "Tipo de error HTTP", example = "Not Found")
    private String error;

    @Schema(description = "Mensaje descriptivo del error", example = "Jugador con id 99 no encontrado")
    private String mensaje;

    @Schema(description = "Fecha y hora en que ocurrió el error", example = "2025-06-01T14:30:00")
    private LocalDateTime timestamp;
}
