package com.padel.rankpadel.dto.response;

import com.padel.rankpadel.enums.Genero;

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
public class CategoriaResponse {

    private Long id;
    private String nombre;
    private int nivel;
    private Integer edadMin;
    private Integer edadMax;
    private Genero genero;

}
