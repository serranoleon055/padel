package com.padel.rankpadel.dto.request;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReglasFixtureRequest {

    @NotNull(message = "Indicá el descanso mínimo (puede ser 0)")
    @Min(value = 0, message = "El descanso no puede ser negativo")
    @Max(value = 600, message = "El descanso no puede superar las 10 horas")
    private Integer descansoMinimoMinutos;

    @Valid
    private List<ReglaCategoria> categorias = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ReglaCategoria {

        @NotNull
        private Long categoriaId;

        /** Null = el sugerido por el formato de cada partido. */
        @Min(value = 15, message = "Un turno no puede durar menos de 15 minutos")
        @Max(value = 300, message = "Un turno no puede durar más de 5 horas")
        private Integer duracionPartidoMinutos;

        /** Null = cualquier club del torneo. */
        private Long lugarFijoId;
    }
}
