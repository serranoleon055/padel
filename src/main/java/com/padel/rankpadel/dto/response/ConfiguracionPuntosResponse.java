package com.padel.rankpadel.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Configuracion de puntos para una ronda de un torneo")
public class ConfiguracionPuntosResponse {

    @Schema(description = "Nombre de la ronda (ej: 'Final', 'Semifinal')")
    private String nombreRonda;

    @Schema(description = "Puntos que recibe el ganador")
    private int puntosGanador;

    @Schema(description = "Puntos que recibe el perdedor")
    private int puntosPerdedor;

    @Schema(description = "Orden de la ronda en el torneo")
    private int orden;
}
