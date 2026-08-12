package com.padel.rankpadel.dto.response;

import java.time.LocalTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * La agenda del día vista por horario, no por cancha. El jugador piensa "quiero jugar a
 * las 20", no "quiero la cancha 3": el select de cancha lo obligaba a probar una por una
 * para descubrir cuál estaba libre.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DisponibilidadSedeResponse {

    private List<FranjaSede> franjas;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class FranjaSede {
        private LocalTime horaInicio;
        /** Cuántas canchas tienen lugar a esta hora. Cero = la franja está llena. */
        private int canchasDisponibles;
        /** El turno más barato que se puede empezar a esta hora, para mostrar "desde $X". */
        private java.math.BigDecimal precioDesde;
        /** Si alguna de las opciones cae dentro de una promoción vigente. */
        private boolean enPromocion;
        private List<CanchaLibre> canchas;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CanchaLibre {
        private Long canchaId;
        private String canchaNombre;
        /** "Techada" / "Descubierta", para que el jugador elija con criterio. */
        private String tipo;
        private List<OpcionDuracion> opciones;
    }
}
