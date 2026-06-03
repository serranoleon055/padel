package com.padel.rankpadel.dto.request;

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
public class ConfiguracionPuntosRequest {

    @NotBlank
    private String nombreRonda;

    @NotNull
    private Integer puntosGanador;

    @NotNull
    private Integer puntosPerdedor;

    @NotNull
    private Integer orden;

}
