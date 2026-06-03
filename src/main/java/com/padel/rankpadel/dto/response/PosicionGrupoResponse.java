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
public class PosicionGrupoResponse {

    private Long id;
    private int posicion;
    private int pj;
    private int pg;
    private int pp;
    private int puntos;
    private int setsGanados;
    private int setsPerdidos;
    private int juegosGanados;
    private int juegosPerdidos;
    private Long parejaId;
    private String parejaNombre;

}
