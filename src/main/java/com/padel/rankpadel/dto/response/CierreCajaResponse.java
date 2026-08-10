package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CierreCajaResponse {

    private LocalDate fecha;

    /** Lo cobrado en el mostrador, abierto por medio de pago. */
    private List<TotalMedio> porMedio;

    /** Solo el efectivo tiene que estar en el cajón al cerrar. */
    private BigDecimal efectivoEsperado;

    /** Total cobrado en el club en el día (todos los medios). */
    private BigDecimal totalMostrador;

    /** Señas online acreditadas hoy en Mercado Pago (no pasan por el cajón). */
    private BigDecimal seniasOnline;

    private BigDecimal totalDelDia;

    /** Turnos de hoy que todavía deben plata. */
    private long turnosConSaldo;
    private BigDecimal saldoPendiente;

    private List<CobroResponse> movimientos;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TotalMedio {
        private String medio;
        private long cantidad;
        private BigDecimal total;
    }
}
