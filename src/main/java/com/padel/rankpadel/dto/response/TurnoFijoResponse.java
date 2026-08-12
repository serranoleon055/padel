package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

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
public class TurnoFijoResponse {

    private Long id;
    private Long canchaId;
    private String canchaNombre;
    private Long lugarId;
    private String lugarNombre;
    private int diaSemana;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private int duracionMin;
    private String clienteNombre;
    private String clienteTelefono;
    private BigDecimal precioPactado;
    private LocalDate vigenteDesde;
    private LocalDate vigenteHasta;
    private boolean activo;
    private String notas;
    /** Última fecha ya generada como reserva concreta. */
    private LocalDate generadoHasta;
}
