package com.padel.rankpadel.enums;

/** En qué momento está la planilla de conteo. */
public enum EstadoRecuento {
    /** Se está contando. Se puede editar y todavía no tocó el stock. */
    BORRADOR,
    /** Ya generó los ajustes. No se edita nunca más. */
    APLICADO
}
