package com.padel.rankpadel.enums;

/**
 * Si la plata entra al cajón o sale.
 *
 * <p>El monto del movimiento va siempre en positivo y el signo lo da el tipo: así nadie
 * puede cargar un egreso en negativo y convertirlo en un ingreso sin que se note.
 */
public enum TipoMovimientoCaja {
    INGRESO,
    EGRESO
}
