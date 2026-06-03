package com.padel.rankpadel.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Posición de un jugador en el ranking")
public class RankingResponse {

    @Schema(description = "Posición actual en el ranking", example = "1")
    private int posicion;

    @Schema(description = "ID del jugador", example = "5")
    private Long jugadorId;

    @Schema(description = "Nombre completo del jugador", example = "Carlos García")
    private String jugadorNombre;

    @Schema(description = "URL de la foto del jugador", example = "https://cdn.rankpadel.com/fotos/carlos.jpg")
    private String jugadorFotoUrl;

    @Schema(description = "ID de la categoría", example = "1")
    private Long categoriaId;

    @Schema(description = "Nombre de la categoría", example = "Primera")
    private String categoriaNombre;

    @Schema(description = "Puntos totales acumulados en el ranking", example = "320")
    private int puntosTotales;

    @Schema(description = "Cantidad de torneos jugados en la temporada", example = "4")
    private int torneosJugados;

    @Schema(description = "Cantidad de partidos ganados", example = "9")
    private int victorias;

    @Schema(description = "Cantidad de partidos perdidos", example = "3")
    private int derrotas;

    @Schema(description = "Tendencia respecto a la posición anterior: SUBE, BAJA o IGUAL", example = "SUBE")
    private String tendencia;
}