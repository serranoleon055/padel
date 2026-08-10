package com.padel.rankpadel.enums;

public enum EstadoPago {
    PENDIENTE,
    APROBADO,
    RECHAZADO,
    EXPIRADO,
    /**
     * El pago entró pero el turno ya no estaba reservado (venció o lo tomó otro).
     * Plata cobrada sin cancha entregada: el club tiene que devolverla.
     */
    APROBADO_SIN_TURNO
}
