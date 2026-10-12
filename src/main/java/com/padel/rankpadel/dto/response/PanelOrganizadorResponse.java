package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.padel.rankpadel.enums.EstadoTorneo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lo que el organizador necesita ver al entrar: qué tiene que resolver hoy, cómo viene
 * cada torneo en marcha y cómo le fue con los que terminaron. Los montos van en null para
 * quien no es el dueño.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PanelOrganizadorResponse {

    private Hoy hoy;
    private List<Alerta> alertas;
    private List<TorneoEnMarcha> torneos;
    private List<TorneoTerminado> terminados;
    private List<CategoriaDemanda> categorias;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Hoy {
        private LocalDate fecha;
        private int partidos;
        private int enJuego;
        private int jugados;
    }

    /** Algo para resolver, con el lugar del panel donde se resuelve. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Alerta {
        /** ALTA: hay que hacer algo ya. MEDIA: conviene mirarlo. */
        private String nivel;
        private String mensaje;
        private Long torneoId;
        /** Pestaña del torneo: horarios, cobros, sedes, finanzas, inscripciones, partidos. */
        private String vista;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TorneoEnMarcha {
        private Long id;
        private String nombre;
        private EstadoTorneo estado;
        private LocalDate fechaInicio;
        private LocalDate fechaFin;
        private int clubes;
        private List<CupoCategoria> categorias;
        private int parejas;
        private int solicitudesPendientes;
        private int partidosSinHorario;
        private int choques;
        private int jugadoresQueDeben;
        private BigDecimal inscripcionesCobrado;
        private BigDecimal inscripcionesPendiente;
        private BigDecimal deudaClubes;
        private BigDecimal resultadoProyectado;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CupoCategoria {
        private String categoria;
        private int inscriptas;
        /** Null = sin cupo. */
        private Integer cupo;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TorneoTerminado {
        private Long id;
        private String nombre;
        private LocalDate fechaFin;
        private int parejas;
        private BigDecimal resultadoProyectado;
        private BigDecimal resultadoReal;
        private BigDecimal porCobrar;
        private BigDecimal porPagar;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoriaDemanda {
        private String categoria;
        private int parejas;
        private int torneos;
        /** Parejas inscriptas sobre cupo, en los torneos con cupo. Null si ninguno tenía. */
        private Integer ocupacionPorcentaje;
    }
}
