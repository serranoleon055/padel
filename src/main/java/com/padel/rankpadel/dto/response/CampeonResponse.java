package com.padel.rankpadel.dto.response;

import java.time.LocalDateTime;

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
public class CampeonResponse {

    private Long torneoId;
    private String torneoNombre;
    private Long categoriaId;
    private String categoriaNombre;
    private Genero genero;
    private Long campeonaId;
    private String campeonaNombre;
    private String subcampeonaNombre;
    private String marcadorFinal;
    private LocalDateTime fecha;
    private String lugarNombre;

}
