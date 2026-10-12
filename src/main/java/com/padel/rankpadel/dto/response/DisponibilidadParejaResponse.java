package com.padel.rankpadel.dto.response;

import java.util.List;

import com.padel.rankpadel.dto.RestriccionHorariaDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cuándo no puede jugar una pareja del torneo. Solo para el panel. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisponibilidadParejaResponse {

    private Long parejaId;
    private String categoriaNombre;
    private String jugador1Nombre;
    private String jugador2Nombre;
    private List<RestriccionHorariaDto> restricciones;
    private String notas;
}
