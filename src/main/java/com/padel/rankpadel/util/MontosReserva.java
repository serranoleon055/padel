package com.padel.rankpadel.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.padel.rankpadel.entity.Pago;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.enums.EstadoPago;

/**
 * Cuánto vale un turno y cuánto de eso ya está cobrado.
 *
 * <p>Un pago de Mercado Pago puede cubrir varios horarios (un turno de dos horas son dos
 * reservas y una sola seña), así que la seña de cada reserva se saca del precio congelado
 * de esa reserva por el porcentaje del pago, no de {@code montoSenia}, que es del lote.
 */
public final class MontosReserva {

    private MontosReserva() {
    }

    public static BigDecimal precio(Reserva reserva) {
        return reserva.getPrecioAplicado() != null ? reserva.getPrecioAplicado() : BigDecimal.ZERO;
    }

    /** Seña online efectivamente acreditada para este horario. */
    public static BigDecimal seniaPagada(Reserva reserva) {
        Pago pago = reserva.getPago();
        if (pago == null || pago.getEstado() != EstadoPago.APROBADO || pago.getPorcentajeSenia() == null) {
            return BigDecimal.ZERO;
        }
        return precio(reserva)
                .multiply(BigDecimal.valueOf(pago.getPorcentajeSenia()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** Lo que todavía falta cobrar de la cancha sola, nunca negativo. */
    public static BigDecimal saldo(Reserva reserva, BigDecimal cobradoEnClub) {
        return saldo(reserva, cobradoEnClub, BigDecimal.ZERO);
    }

    /**
     * Lo que falta cobrar del turno entero: la cancha más lo que el grupo consumió y
     * dejó anotado en la cuenta, menos la seña y lo ya cobrado en el mostrador.
     *
     * @param consumoACuenta ventas del turno sin medio de pago, es decir, todavía impagas.
     */
    public static BigDecimal saldo(Reserva reserva, BigDecimal cobradoEnClub, BigDecimal consumoACuenta) {
        BigDecimal cobrado = cobradoEnClub != null ? cobradoEnClub : BigDecimal.ZERO;
        BigDecimal consumo = consumoACuenta != null ? consumoACuenta : BigDecimal.ZERO;
        return precio(reserva).add(consumo).subtract(seniaPagada(reserva)).subtract(cobrado)
                .max(BigDecimal.ZERO);
    }
}
