package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    /**
     * Jornada que el club está atendiendo, que no siempre es la fecha de hoy: antes de la
     * apertura, la sesión viva es la que arrancó ayer. Todos los números del panel están
     * medidos sobre ella, y el front la usa para pedir la disponibilidad del mismo día.
     */
    private LocalDate fechaJornada;

    private HomeSummaryResponse summary;
    private TemporadaResponse temporadaActiva;
    private List<TorneoResponse> ultimosTorneos;
    private List<TorneoResponse> torneosEnVivo;
    private long canchasTotales;
    private long canchasOcupadasAhora;
    private long canchasLibresAhora;
    private long turnosDisponiblesHoy;
    /** El mismo hueco contado con cada duración que vende el club. No es una partición. */
    private List<DisponibilidadDuracionResponse> disponiblesPorDuracion;
    private List<CanchaEstadoDashboardResponse> canchas;
    private long reservasHoy;
    private long reservasPendientes;
    private long solicitudesPendientes;
    private long torneosFinalizados;
    private long torneosEnInscripcion;
    private BigDecimal ingresoEstimadoHoy;
    private List<Long> turnosPorDiaSemana;
    private List<TurnoResumenResponse> proximosTurnosHoy;
    private List<ReservaResponse> reservasPendientesLista;
    private List<SolicitudInscripcionResponse> solicitudesPendientesLista;

}
