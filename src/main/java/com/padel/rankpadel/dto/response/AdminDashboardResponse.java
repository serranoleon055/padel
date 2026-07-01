package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
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
    private long canchasTotales;
    private long canchasOcupadasAhora;
    private long canchasLibresAhora;
    private long turnosDisponiblesHoy;
    private List<CanchaEstadoDashboardResponse> canchas;
    private long reservasHoy;
    private long reservasPendientes;
    private long solicitudesPendientes;
    private BigDecimal ingresoEstimadoHoy;
    private List<Long> turnosPorDiaSemana;
    private List<TurnoResumenResponse> proximosTurnosHoy;
    private List<ReservaResponse> reservasPendientesLista;
    private List<SolicitudInscripcionResponse> solicitudesPendientesLista;

}
