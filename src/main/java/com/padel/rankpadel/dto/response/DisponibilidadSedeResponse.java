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
        /**
         * Qué parte del turno se paga como seña, en porcentaje. Viaja hasta el jugador
         * porque la pantalla de reserva le anuncia el monto ANTES de mandarlo a pagar: con
         * un 50 fijo en el front, una cancha configurada al 30 mostraba una cifra y Mercado
         * Pago cobraba otra. El que manda es el de la cancha (ver PagoService).
         */
        private Integer seniaPorcentaje;
        private List<OpcionDuracion> opciones;
    }
}
