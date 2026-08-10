package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

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
public class ClienteResponse {

    private Long id;
    private String nombre;
    private String telefono;
    private String email;
    private String notas;
    /** true si además está cargado como jugador de torneos. */
    private boolean esJugador;

    private long turnosTotales;
    private long turnosJugados;
    private long turnosCaidos;
    /** Reservó y no vino. */
    private long turnosNoShow;
    private BigDecimal gastoAcumulado;
    private LocalDate ultimoTurno;
}
