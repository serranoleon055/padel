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

    /** Lo que todavía falta cobrar, nunca negativo. */
    public static BigDecimal saldo(Reserva reserva, BigDecimal cobradoEnClub) {
        BigDecimal cobrado = cobradoEnClub != null ? cobradoEnClub : BigDecimal.ZERO;
        BigDecimal restante = precio(reserva).subtract(seniaPagada(reserva)).subtract(cobrado);
        return restante.max(BigDecimal.ZERO);
    }
}
