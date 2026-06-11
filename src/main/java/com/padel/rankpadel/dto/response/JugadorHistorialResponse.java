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
public class JugadorHistorialResponse {

    private JugadorResponse jugador;

    private List<RankingResponse> ranking;

    private List<PartidoResponse> partidos;

    private List<TorneoHistorialItem> torneos;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TorneoHistorialItem {
        private Long torneoId;
        private String torneoNombre;
        private String categoriaNombre;
        private String estado;
        private String fechaInicio;
        private String fechaFin;
        private boolean fueGanador;
        private String mejorRonda;
        private int puntosObtenidos;
    }

}
