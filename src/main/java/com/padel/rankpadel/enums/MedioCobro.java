package com.padel.rankpadel.enums;

/**
 * Cómo entró la plata. Importa para el cierre de caja: solo el efectivo tiene que
 * estar físicamente en el cajón al final del día.
 */
public enum MedioCobro {
    EFECTIVO,
    TRANSFERENCIA,
    TARJETA,
    MERCADO_PAGO,
    OTRO
}
