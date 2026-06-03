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
public class AdminDashboardResponse {

    private HomeSummaryResponse summary;
    private TemporadaResponse temporadaActiva;
    private List<TorneoResponse> ultimosTorneos;
    private List<TorneoResponse> torneosEnVivo;
    private List<Long> evolucionMeses;

}
