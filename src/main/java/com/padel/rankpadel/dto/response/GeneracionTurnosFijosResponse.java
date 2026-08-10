package com.padel.rankpadel.dto.response;

import java.time.LocalDate;
import java.util.List;

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
public class GeneracionTurnosFijosResponse {

    private int generadas;
    private List<Conflicto> conflictos;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Conflicto {
        private Long turnoFijoId;
        private String clienteNombre;
        private String canchaNombre;
        private LocalDate fecha;
        private String horaInicio;
        private String motivo;
    }
}
