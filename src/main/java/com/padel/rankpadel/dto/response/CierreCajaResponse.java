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

    /** Un movimiento por pago real, no por reserva: un turno de dos horas es una línea. */
    private List<MovimientoCajaResponse> movimientos;

    /** Egresos del día (todos los medios). */
    private BigDecimal egresos;
    /** Parte de los egresos que salió del cajón. */
    private BigDecimal egresosEfectivo;
    /** Ingresos menos egresos: lo que de verdad quedó. */
    private BigDecimal resultado;
    private List<GastoResponse> gastos;

    /** Ventas de mostrador del día: pelotas, bebidas, alquiler de paletas. */
    private List<VentaResponse> ventas;
    private BigDecimal totalVentas;

    /**
     * El arqueo firmado, si el día ya se cerró. Mientras es null, el día sigue abierto y
     * los totales de arriba se recalculan en cada consulta.
     */
    private Arqueo arqueo;

    /** Lo que se anuló en el día. No suma en ningún total; está para poder auditarlo. */
    private List<CobroResponse> cobrosAnulados;
    private List<VentaResponse> ventasAnuladas;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Arqueo {
        private BigDecimal efectivoContado;
        /** Contado menos esperado. Negativo = faltó plata en el cajón. */
        private BigDecimal diferencia;
        private String cerradoPor;
        private java.time.LocalDateTime cerradoEn;
        private String notas;
    }

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
