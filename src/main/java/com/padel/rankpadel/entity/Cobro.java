package com.padel.rankpadel.entity;

import java.math.BigDecimal;
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
 * Plata que entró en el club por un turno. La seña online vive en {@link Pago};
 * acá va lo que se cobra en el mostrador (el saldo, o el turno completo si no
 * hubo seña).
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "cobros")
public class Cobro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id")
    private Reserva reserva;

    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    private MedioPago medio;

    private LocalDateTime cobradoEn;

    /** Usuario del panel que lo registró: si falta plata hay que saber quién cobró. */
    private String registradoPor;

    private String notas;

    /**
     * Anulación. Es baja lógica y no borrado: la fila sale de todos los totales pero
     * queda, porque un movimiento de plata que desaparece sin rastro deja el cierre de un
     * día pasado cambiando solo y sin nadie a quien preguntarle.
     */
    private LocalDateTime anuladoEn;
    private String anuladoPor;
    private String motivoAnulacion;

    public boolean estaAnulado() {
        return anuladoEn != null;
    }
}
