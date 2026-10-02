package com.padel.rankpadel.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.padel.rankpadel.enums.ConceptoMovimientoCaja;
import com.padel.rankpadel.enums.MedioPago;
import com.padel.rankpadel.enums.TipoMovimientoCaja;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Plata que entra o sale del cajón y no es un turno ni una venta del kiosco: el fondo con
 * el que se abre el día, un depósito al banco, un retiro del dueño, el alquiler del salón.
 *
 * <p>Es una entidad aparte de {@link Cobro} a propósito. {@code cobros.reserva_id} es NOT
 * NULL, y ese NOT NULL es lo que hace que {@code MontosReserva} sea la única fuente de
 * verdad del saldo de un turno y lo que permite validar "no cobrar de más". Un depósito al
 * banco no es el pago parcial de nada.
 *
 * <p>Y es distinta de {@link Gasto}: un gasto pesa en el estado de resultados, esto no.
 * Los dos bajan el efectivo esperado del arqueo; solo el gasto baja la rentabilidad.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "movimientos_caja")
public class MovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Jornada del club a la que pertenece esta plata. Se estampa al registrar y no se
     * recalcula nunca, igual que {@code Cobro.jornada} (ver V55 y V61).
     */
    private LocalDate jornada;

    /** Instante en que se registró. */
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    private TipoMovimientoCaja tipo;

    @Enumerated(EnumType.STRING)
    private ConceptoMovimientoCaja concepto;

    private String descripcion;

    /** Siempre positivo: el signo lo da {@link #tipo}. */
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    private MedioPago medio;

    /** Opcional: si el movimiento es de alguien con ficha, queda enlazado. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    /** Usuario del panel que lo registró: si falta plata hay que saber quién la movió. */
    private String registradoPor;

    private String notas;

    private LocalDateTime creadoEn;

    /**
     * Anulación. Baja lógica, mismo criterio que cobros, ventas y gastos: un movimiento de
     * plata que desaparece sin rastro deja el arqueo de un día pasado cambiando solo.
     */
    private LocalDateTime anuladoEn;
    private String anuladoPor;
    private String motivoAnulacion;

    public boolean estaAnulado() {
        return anuladoEn != null;
    }

    /** Lo que este movimiento le suma (o le resta) al efectivo del cajón. */
    public BigDecimal aporteConSigno() {
        return TipoMovimientoCaja.EGRESO.equals(tipo) ? monto.negate() : monto;
    }
}
