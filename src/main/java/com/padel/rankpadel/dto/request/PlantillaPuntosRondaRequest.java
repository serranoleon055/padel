package com.padel.rankpadel.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class PlantillaPuntosRondaRequest {

    @NotBlank
    private String nombreRonda;

    @NotNull
    @Min(0)
    private Integer puntosGanador;

    // Puede ser negativo para penalizar la derrota (el total del jugador se pisa en 0).
    @NotNull
    private Integer puntosPerdedor;

    @NotNull
    @Min(1)
    private Integer orden;
}
