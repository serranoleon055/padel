package com.padel.rankpadel.util;

import java.time.LocalTime;
import java.util.Comparator;

/**
 * Orden de los horarios dentro de la jornada del club, que no es el orden del reloj.
 *
 * <p>Un club que abre a las 10 y cierra a las 2 tiene las 00 y la 1 como sus dos últimos
 * turnos, no como los primeros. Ordenar por {@link LocalTime} los mandaba al principio de
 * la grilla y de todos los listados, con el turno de la madrugada arriba del de la mañana.
 * Todo lo que muestre horarios de una jornada tiene que ordenar con esto.
 */
public final class OrdenJornada {

    private static final int MINUTOS_DEL_DIA = 24 * 60;

    private OrdenJornada() {
    }

    /**
     * Minutos que pasaron desde la apertura. Lo anterior a la apertura pertenece a la
     * madrugada del día siguiente, así que suma una vuelta de reloj y queda al final.
     */
    public static int minutosDesdeApertura(LocalTime hora, LocalTime apertura) {
        if (hora == null) {
            return MINUTOS_DEL_DIA;
        }
        if (apertura == null) {
            return hora.toSecondOfDay() / 60;
        }
        int minutos = (hora.toSecondOfDay() - apertura.toSecondOfDay()) / 60;
        return minutos < 0 ? minutos + MINUTOS_DEL_DIA : minutos;
    }

    /** Comparador de horarios para una jornada que arranca en {@code apertura}. */
    public static Comparator<LocalTime> comparador(LocalTime apertura) {
        return Comparator.comparingInt(hora -> minutosDesdeApertura(hora, apertura));
    }
}
