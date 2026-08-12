package com.padel.rankpadel.enums;

/**
 * Por qué se movió el stock. Un faltante sin motivo no se puede explicar al cierre del
 * mes, así que toda variación queda registrada con uno de estos.
 */
public enum MotivoMovimientoStock {
    /** Salió por una venta en el mostrador. */
    VENTA,
    /** Entró mercadería comprada al proveedor. */
    COMPRA,
    /** Corrección manual del club tras contar la vitrina. */
    AJUSTE,
    /** Volvió al stock porque se anuló la venta. */
    ANULACION,
    /** Se rompió, se venció o se perdió. */
    MERMA
}
