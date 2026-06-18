package com.padel.rankpadel.dto.response;

import com.padel.rankpadel.enums.EstadoPareja;

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
public class ParejaResponse {

    private Long id;
    private Long jugador1Id;
    private Long jugador2Id;
    private String jugador1Nombre;
    private String jugador2Nombre;
    private Long categoriaId;
    private String categoriaNombre;
    private boolean esCabezaDeSerie;
    private EstadoPareja estado;

}
