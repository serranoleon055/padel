package com.padel.rankpadel.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Lo que propone el armado automático. No se guarda nada hasta aplicarlo. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropuestaFixtureResponse {

    private List<Asignacion> asignaciones;
    private List<SinLugar> sinLugar;
    /** Partidos que ya tenían horario y se respetaron. */
    private int respetados;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Asignacion {
        private Long partidoId;
        private String descripcion;
        private String categoriaNombre;
        private Long canchaId;
        private String canchaNombre;
        private Long lugarId;
        private String lugarNombre;
        private LocalDateTime inicio;
        private LocalDateTime fin;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SinLugar {
        private Long partidoId;
        private String descripcion;
        private String motivo;
    }
}
