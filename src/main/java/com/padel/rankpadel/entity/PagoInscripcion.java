package com.padel.rankpadel.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.padel.rankpadel.enums.MedioPago;

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
 * Lo que pagó un jugador por su inscripción. Es por jugador y no por pareja porque así
 * pasa en la cancha: uno transfiere el lunes, el compañero paga en efectivo el sábado, y
 * hay que saber cuál de los dos todavía debe. Puede haber varios pagos (seña y saldo).
 *
 * <p>Si vino de Mercado Pago, {@link #pagoOnline} apunta al pago original.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "pagos_inscripcion")
public class PagoInscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pareja_id")
    private Pareja pareja;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jugador_id")
    private Jugador jugador;

    private LocalDate fecha;
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    private MedioPago medio;

    private String notas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pago_id")
    private Pago pagoOnline;

    private String registradoPor;
    private LocalDateTime creadoEn;

    private LocalDateTime anuladoEn;
    private String anuladoPor;
    private String motivoAnulacion;

    public boolean estaAnulado() {
        return anuladoEn != null;
    }
}
