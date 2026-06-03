package com.padel.rankpadel.dto.request;

import jakarta.validation.constraints.NotNull;
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
public class ParejaRequest {

    @NotNull
    private Long jugador1Id;

    @NotNull
    private Long jugador2Id;

    @NotNull
    private Long categoriaId;

    private boolean esCabezaDeSerie;

}
