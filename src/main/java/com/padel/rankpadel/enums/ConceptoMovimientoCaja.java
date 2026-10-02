package com.padel.rankpadel.enums;

/**
 * Qué fue ese movimiento de plata que no es un turno ni una venta del kiosco.
 *
 * <p>Ninguno de estos es un {@code Gasto}: un gasto pesa en el estado de resultados (luz,
 * insumos, sueldos) y estos no. Un depósito al banco cambia la plata de lugar, no la hace
 * desaparecer; un retiro del dueño es distribución de utilidad, no un costo del club.
 * Todos bajan o suben el efectivo esperado del arqueo; ninguno mueve la rentabilidad.
 */
public enum ConceptoMovimientoCaja {

    /** El fondo con el que arranca el día: la plata que queda en el cajón para dar vuelto. */
    APERTURA,

    /** Lo que se saca del cajón y se lleva al banco. */
    DEPOSITO_BANCO,

    /** Plata que retira el dueño. No es un gasto del club. */
    RETIRO_DUENIO,

    /** Un préstamo dado o devuelto. */
    PRESTAMO,

    /** Un cobro que no es de un turno: el alquiler del salón, una clase, un evento. */
    COBRO_VARIO,

    /** Corrección declarada: apareció o faltó plata y se deja asentado con su motivo. */
    AJUSTE,

    OTRO;

    /**
     * Si solo lo puede cargar el dueño. Son los movimientos que sirven para explicar un
     * faltante: si el empleado puede anotar "retiro del dueño" o "depósito al banco" por
     * su cuenta, cualquier diferencia del arqueo tiene una tapa a mano.
     *
     * <p>No se puede resolver en {@code SecurityConfig} porque depende del cuerpo del
     * pedido, no de la ruta: la restricción vive en el servicio y tiene su test.
     */
    public boolean exigeDuenio() {
        return this == RETIRO_DUENIO || this == DEPOSITO_BANCO || this == PRESTAMO
                || this == AJUSTE;
    }

    /**
     * El único sentido posible de este concepto, o null si va en los dos.
     *
     * <p>Un préstamo se da y se devuelve, y un ajuste puede ser de más o de menos: esos
     * necesitan que la persona elija. Los demás no, y dejarlos elegir permitiría cargar un
     * "retiro del dueño" que suma plata al cajón.
     */
    public TipoMovimientoCaja tipoForzado() {
        return switch (this) {
            case APERTURA, COBRO_VARIO -> TipoMovimientoCaja.INGRESO;
            case DEPOSITO_BANCO, RETIRO_DUENIO -> TipoMovimientoCaja.EGRESO;
            case PRESTAMO, AJUSTE, OTRO -> null;
        };
    }

    /**
     * Si este movimiento es plata que el club GANÓ o PERDIÓ, y no plata que solo cambió de
     * lugar.
     *
     * <p>Es la diferencia entre el efectivo del cajón y el resultado del negocio, y hay que
     * tenerla clara porque los dos números salen en la misma pantalla. Llevar la plata al
     * banco, retirarla el dueño, prestarla o declarar un ajuste **mueve el cajón y no mueve
     * la rentabilidad**: la plata sigue siendo del club (o ya era suya). El alquiler del
     * salón o una clase particular sí son ingresos.
     *
     * <p>{@code OTRO} queda del lado conservador —no afecta el resultado— para que un
     * movimiento mal clasificado no infle la facturación en silencio. Un egreso operativo
     * de verdad es un {@code Gasto}, que tiene su categoría y su fecha contable.
     */
    public boolean afectaResultado() {
        return this == COBRO_VARIO;
    }
}
