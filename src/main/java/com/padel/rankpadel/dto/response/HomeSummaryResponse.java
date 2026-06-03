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
public class HomeSummaryResponse {

    private long torneosActivos;
    private long jugadoresRegistrados;
    private long partidosFinalizados;
    private long partidosEnVivo;
    private long torneosTotales;
    private long categoriasActivas;

}
