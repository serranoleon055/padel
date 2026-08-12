package com.padel.rankpadel.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import com.padel.rankpadel.enums.EstadoReserva;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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
@Entity
@Table(name = "reservas")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancha_id")
    private Cancha cancha;

    private LocalDate fecha;
    private LocalTime horaInicio;
    /** Derivada de {@code horaInicio + duracionMin}. Se guarda porque hay queries que filtran por ella. */
    private LocalTime horaFin;

    /**
     * Cuánto dura el turno. Un turno es UNA reserva: antes uno de dos horas eran dos
     * filas de una hora, y eso hacía que se contara doble en todos lados.
     */
    @Builder.Default
    private int duracionMin = 60;

    // Precio del turno congelado al reservar: la facturación histórica no debe
    // cambiar cuando el club actualiza la tarifa de la cancha.
    private BigDecimal precioAplicado;

    @Enumerated(EnumType.STRING)
    private EstadoReserva estado;

    private String clienteNombre;
    private String clienteTelefono;
    private String codigo;
    private LocalDateTime creadoEn;
    private LocalDateTime confirmadoEn;
    private LocalDateTime expiraEn;

    /**
     * Bloques de 30 minutos que este turno tiene tomados. Vacío = el horario está libre
     * (turno cancelado, rechazado o vencido). Es la fuente de la garantía de unicidad.
     */
    @Builder.Default
    @OneToMany(mappedBy = "reserva", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservaSlot> slots = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pago_id")
    private Pago pago;

    /** Turno fijo que la generó, si viene de un abono. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_fijo_id")
    private TurnoFijo turnoFijo;

    /**
     * Ficha del cliente. Los campos {@code clienteNombre} y {@code clienteTelefono} se
     * mantienen como estaban: son lo que la persona escribió en ese momento y no deben
     * cambiar si después se corrige la ficha.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;
}
