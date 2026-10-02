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

    /**
     * Solo el efectivo tiene que estar en el cajón al cerrar. La cuenta completa es:
     * fondo inicial + cobros y ventas en efectivo + movimientos de ingreso en efectivo
     * − gastos en efectivo − movimientos de egreso en efectivo.
     */
    private BigDecimal efectivoEsperado;

    /** Si la jornada ya declaró su fondo inicial. Mientras sea false, falta abrir la caja. */
    private boolean cajaAbierta;

    /** El efectivo con el que arrancó el cajón, declarado al abrir. */
    private BigDecimal fondoInicial;

    /** Movimientos que no son turnos ni ventas: depósitos, retiros, cobros varios. */
    private BigDecimal movimientosIngreso;
    private BigDecimal movimientosEgreso;
    private List<MovimientoSueltoResponse> movimientosSueltos;
    private List<MovimientoSueltoResponse> movimientosSueltosAnulados;

    /**
     * Quién cobró qué. El dato estaba en la base desde V44 (`registradoPor` en cada cobro
     * y cada venta) y no se mostraba en ningún lado, así que el dueño no tenía forma de
     * saber si el faltante aparece siempre en el mismo turno. Solo lo ve el dueño.
     */
    private List<TotalPorUsuario> porUsuario;

    /** Total cobrado en el club en el día (todos los medios). */
    private BigDecimal totalMostrador;

    /**
     * Señas de TURNO acreditadas hoy en Mercado Pago (no pasan por el cajón). Las
     * inscripciones a torneos también entran por Mercado Pago y van aparte: antes se
     * sumaban acá y el club veía como seña de cancha la plata de un torneo.
     */
    private BigDecimal seniasOnline;

    /** Inscripciones a torneos acreditadas hoy en Mercado Pago. */
    private BigDecimal inscripcionesOnline;

    private BigDecimal totalDelDia;

    /** Turnos de hoy que todavía deben plata. */
    private long turnosConSaldo;
    private BigDecimal saldoPendiente;

    /** Un movimiento por pago real, no por reserva: un turno de dos horas es una línea. */
    private List<MovimientoCajaResponse> movimientos;

    /**
     * Egresos de la jornada (todos los medios). Viaja en null para el empleado: el
     * detalle de los gastos y la rentabilidad son del dueño.
     */
    private BigDecimal egresos;
    /**
     * Parte de los egresos que salió del cajón. Este SÍ lo ve el empleado, porque sin él
     * el efectivo esperado no se puede explicar y un arqueo que no se entiende no se
     * puede firmar.
     */
    private BigDecimal egresosEfectivo;
    /** Ingresos menos egresos: lo que de verdad quedó. Null para el empleado. */
    private BigDecimal resultado;
    /** Detalle de los egresos. Vacío para el empleado. */
    private List<GastoResponse> gastos;
    private List<GastoResponse> gastosAnulados;

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

    /** Lo que movió cada usuario del panel en la jornada. */
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TotalPorUsuario {
        private String usuario;
        private BigDecimal cobros;
        private BigDecimal ventas;
        private BigDecimal movimientos;
        private BigDecimal total;
        /** La parte que tiene que estar en el cajón: es lo que se le pide al entregar. */
        private BigDecimal efectivo;
        private long operaciones;
    }
}
