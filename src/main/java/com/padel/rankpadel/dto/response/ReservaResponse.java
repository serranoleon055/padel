package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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
public class ReservaResponse {

    private Long id;
    private Long canchaId;
    private String canchaNombre;
    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    /** Minutos que dura el turno: 60, 90 o 120. */
    private int duracionMin;
    private String estado;
    private String clienteNombre;
    private String clienteTelefono;
    private String codigo;
    private String estadoPago;
    private BigDecimal montoSenia;
    private BigDecimal montoTotal;
    /** Precio del turno congelado al reservar. */
    private BigDecimal precioAplicado;
    /** true si la generó un turno fijo (abono). */
    private boolean turnoFijo;
    /** Ficha del cliente, para saltar al historial desde el panel. */
    private Long clienteId;
    /** Seña online ya acreditada para este horario. */
    private BigDecimal seniaPagada;
    /** Cobrado en el mostrador. */
    private BigDecimal totalCobrado;
    /** Lo que todavía falta cobrar. */
    /** Consumo del kiosco anotado en la cuenta del turno, todavía sin pagar. */
    private BigDecimal consumoACuenta;

    /** Cancha + consumo: lo que el turno vale en total. */
    private BigDecimal totalTurno;

    private BigDecimal saldoPendiente;

    /**
     * Momento real en que arranca el turno, con la jornada ya resuelta.
     *
     * <p>{@code fecha} + {@code horaInicio} no alcanzan: un turno de la 1 AM se guarda con
     * la fecha del día anterior porque pertenece a la sesión que arrancó esa noche, y
     * pegarlos daba un instante 24 horas antes del real. Con esto el mostrador sabe sin
     * ninguna cuenta si el turno está jugándose, ya terminó o todavía no empezó. Solo lo
     * completan los listados que lo necesitan; en el resto viaja nulo.
     */
    private LocalDateTime inicioReal;
}
