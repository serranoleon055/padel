package com.padel.rankpadel.dto.response;

import java.time.LocalDate;

/**
 * La jornada que el club está atendiendo ahora. Existe para que las pantallas del
 * mostrador abran en el día correcto sin repetir la regla en el front: a las 00:30 el día
 * de calendario ya cambió, pero lo que se está jugando pertenece a la sesión de anoche.
 */
public record JornadaActualResponse(LocalDate fecha) {
}
