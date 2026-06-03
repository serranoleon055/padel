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

    /** Posiciones actuales en el ranking por categoría */
    private List<RankingResponse> ranking;

    /** Partidos jugados por este jugador, ordenados por fecha desc */
    private List<PartidoResponse> partidos;

    /** Torneos en los que participó, con su resultado final */
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
