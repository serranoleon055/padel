package com.padel.rankpadel.dto.response;

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
public class GrupoResponse {

    private Long id;
    private String nombre;
    private Long categoriaId;
    private String categoriaNombre;
    private List<PosicionGrupoResponse> posiciones;

}
