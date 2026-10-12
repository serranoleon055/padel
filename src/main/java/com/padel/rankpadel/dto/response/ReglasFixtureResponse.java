package com.padel.rankpadel.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReglasFixtureResponse {

    private int descansoMinimoMinutos;
    private List<ReglaCategoria> categorias;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReglaCategoria {
        private Long categoriaId;
        private String categoriaNombre;
        private Integer duracionPartidoMinutos;
        /** Lo que se usa si no se carga una duración: "zona 45 min · finales 60 min". */
        private String duracionSugerida;
        private Long lugarFijoId;
        private String lugarFijoNombre;
    }
}
