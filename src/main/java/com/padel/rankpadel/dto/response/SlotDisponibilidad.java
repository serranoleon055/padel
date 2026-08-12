package com.padel.rankpadel.dto.response;

import java.time.LocalTime;
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
public class SlotDisponibilidad {

    private LocalTime horaInicio;
    /** Fin del turno más corto que se puede empezar a esta hora. */
    private LocalTime horaFin;
    private boolean disponible;
    /** Duraciones que entran a partir de esta hora, con su precio. Vacío = no hay lugar. */
    private List<OpcionDuracion> opciones;
}
