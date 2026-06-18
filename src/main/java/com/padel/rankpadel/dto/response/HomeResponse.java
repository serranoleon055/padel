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
public class HomeResponse {

    private HomeSummaryResponse summary;
    private TorneoResponse torneoDestacado;
    private List<TorneoResponse> proximosTorneos;
    private List<TorneoResponse> torneosEnVivo;
    private List<PartidoResponse> partidosEnVivo;
    private List<PartidoResponse> ultimosResultados;
    private List<CampeonResponse> ultimosCampeones;
    private List<RankingResponse> rankingDestacado;

}
