package com.padel.rankpadel.dto.response;

import java.time.LocalTime;

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
public class TurnoResumenResponse {

    private Long canchaId;
    private String canchaNombre;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String clienteNombre;
}
