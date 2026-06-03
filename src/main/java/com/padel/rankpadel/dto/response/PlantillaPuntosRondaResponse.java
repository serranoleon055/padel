package com.padel.rankpadel.dto.response;

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
public class PlantillaPuntosRondaResponse {

    private Long id;
    private String nombreRonda;
    private int puntosGanador;
    private int puntosPerdedor;
    private int orden;
}
