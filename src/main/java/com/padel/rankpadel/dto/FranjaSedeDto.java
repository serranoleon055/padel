package com.padel.rankpadel.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Día y horario en que el club cede sus canchas. Sirve igual de ida que de vuelta. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FranjaSedeDto {

    @NotNull(message = "Indicá el día de la franja")
    private LocalDate fecha;

    @NotNull(message = "Indicá desde qué hora")
    private LocalTime horaDesde;

    @NotNull(message = "Indicá hasta qué hora")
    private LocalTime horaHasta;
}
