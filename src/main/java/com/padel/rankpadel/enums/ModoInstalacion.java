package com.padel.rankpadel.enums;

/**
 * Para quién es esta instalación. El código es uno solo: lo que cambia es qué módulos
 * se ofrecen.
 *
 * <ul>
 * <li>{@link #CLUB}: el club que alquila canchas, vende en el mostrador y además
 * organiza torneos en su sede.</li>
 * <li>{@link #ORGANIZADOR}: la organización que arma torneos en canchas de terceros.
 * No tiene turnos, caja ni stock; tiene varios clubes por torneo y le paga a cada
 * uno.</li>
 * </ul>
 */
public enum ModoInstalacion {
    CLUB, ORGANIZADOR
}
