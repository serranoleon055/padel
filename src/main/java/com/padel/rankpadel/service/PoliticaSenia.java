package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cuánto de un turno se paga por adelantado.
 *
 * <p>Vive aparte de {@code PagoService} porque lo necesitan dos lados que no se pueden
 * llamar entre sí: el que cobra la seña y el que la <b>anuncia</b> en la grilla pública
 * antes de mandar a pagar. Cuando el porcentaje estaba fijo en el front, una cancha
 * configurada al 30% mostraba un monto en pantalla y Mercado Pago cobraba otro.
 */
@Component
public class PoliticaSenia {

    @Value("${app.pagos.porcentaje-senia-default:50}")
    private int porcentajeDefault;

    /**
     * El porcentaje que realmente se va a cobrar. Un valor sin sentido (0, negativo o más
     * de 100) se ignora y manda el del club: es un error de carga, no una decisión.
     */
    public int porcentaje(Integer configurado) {
        int porcentaje = configurado != null ? configurado : porcentajeDefault;
        return porcentaje <= 0 || porcentaje > 100 ? porcentajeDefault : porcentaje;
    }

    /** La parte del total que entra como seña, redondeada al centavo. */
    public BigDecimal montoSenia(BigDecimal montoTotal, int porcentaje) {
        return montoTotal.multiply(BigDecimal.valueOf(porcentaje))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
