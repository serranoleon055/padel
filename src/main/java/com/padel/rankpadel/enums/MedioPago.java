package com.padel.rankpadel.enums;

/**
 * Cómo se movió la plata, tanto al cobrar como al pagar un gasto. Importa para el
 * cierre de caja: solo lo que entró y salió en efectivo afecta lo que tiene que estar
 * físicamente en el cajón al final del día.
 */
public enum MedioPago {
    EFECTIVO,
    TRANSFERENCIA,
    TARJETA,
    MERCADO_PAGO,
    OTRO
}
