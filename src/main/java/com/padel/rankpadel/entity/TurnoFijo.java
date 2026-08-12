package com.padel.rankpadel.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Entity;
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
 * Turno fijo / abono: el mismo cliente, la misma cancha, el mismo día y hora, todas las
 * semanas. Las reservas concretas las genera {@code TurnoFijoService} con anticipación.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "turnos_fijos")
public class TurnoFijo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancha_id")
    private Cancha cancha;

    /** ISO: 1 = lunes ... 7 = domingo. */
    private int diaSemana;

    private LocalTime horaInicio;

    /** Cuánto dura el turno del abono, en minutos (60, 90 o 120). */
    @Builder.Default
    private int duracionMin = 60;

    private String clienteNombre;
    private String clienteTelefono;

    /**
     * Ficha del cliente. Los campos de arriba se conservan como texto crudo —lo que el
     * club escribió al cargar el abono— igual que en {@link Reserva}. La ficha se enlaza
     * por clave foránea y no por comparación de teléfonos: escritos distintos, "385 689
     * 4061" y "3856894061" son la misma persona, y comparando texto no lo eran.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    /** Precio pactado del turno completo. Si es null, se usa la tarifa de la cancha. */
    private BigDecimal precioPactado;

    private LocalDate vigenteDesde;

    /** Null = sin fecha de corte. */
    private LocalDate vigenteHasta;

    @Builder.Default
    private boolean activo = true;

    private String notas;
    private LocalDateTime creadoEn;
}
