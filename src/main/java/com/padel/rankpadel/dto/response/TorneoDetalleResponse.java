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
public class TorneoDetalleResponse {

    private TorneoResponse torneo;
    private List<ParejaResponse> parejas;
    private List<PartidoResponse> partidos;
    private List<CampeonResponse> campeones;

}
