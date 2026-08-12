package com.padel.rankpadel.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuántos turnos de una duración concreta quedan por vender hoy. Las duraciones no se
 * suman entre sí: el mismo hueco de dos horas se cuenta como un turno de 120 y como dos
 * de 60.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DisponibilidadDuracionResponse {

    private int minutos;
    private long turnos;
}
