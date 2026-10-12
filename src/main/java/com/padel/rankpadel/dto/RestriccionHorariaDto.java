package com.padel.rankpadel.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un horario en que la pareja no puede jugar: un día del torneo o un día de la semana. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestriccionHorariaDto {

    private LocalDate fecha;

    @Min(value = 1, message = "El día de la semana va de 1 (lunes) a 7 (domingo)")
    @Max(value = 7, message = "El día de la semana va de 1 (lunes) a 7 (domingo)")
    private Integer diaSemana;

    @NotNull(message = "Indicá desde qué hora no pueden")
    private LocalTime horaDesde;

    @NotNull(message = "Indicá hasta qué hora no pueden")
    private LocalTime horaHasta;
}
