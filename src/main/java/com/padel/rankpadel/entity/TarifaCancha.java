package com.padel.rankpadel.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Precio de una cancha en una franja de días y horas: "viernes y sábado de 20 a 2".
 * Si ninguna franja cubre el horario, se usa {@code Cancha.precioPorHora}.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "tarifas_cancha")
public class TarifaCancha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancha_id")
    private Cancha cancha;

    private String nombre;

    /** ISO, separados por coma: "1,2,3,4,5". */
    private String diasSemana;

    private LocalTime horaDesde;

    /** Si es menor o igual a {@code horaDesde}, la franja cruza la medianoche. */
    private LocalTime horaHasta;

    private BigDecimal precioPorHora;

    @Builder.Default
    private boolean activo = true;

    /**
     * ¿Esta franja cubre el turno del día {@code fecha} que empieza a las {@code hora}?
     *
     * <p>El día se toma de la fecha de la reserva, que es la de la sesión: un turno de la
     * 1 de la mañana del sábado pertenece a la noche del viernes, y así lo agenda el
     * sistema. Por eso una franja "viernes de 20 a 2" lo cubre.
     */
    public boolean cubre(LocalDate fecha, LocalTime hora) {
        if (!activo || !diaIncluido(fecha)) {
            return false;
        }
        if (horaDesde.isBefore(horaHasta)) {
            return !hora.isBefore(horaDesde) && hora.isBefore(horaHasta);
        }
        // Cruza medianoche: 20:00 -> 02:00
        return !hora.isBefore(horaDesde) || hora.isBefore(horaHasta);
    }

    private boolean diaIncluido(LocalDate fecha) {
        if (diasSemana == null || diasSemana.isBlank()) {
            return true;
        }
        String dia = String.valueOf(fecha.getDayOfWeek().getValue());
        for (String token : diasSemana.split(",")) {
            if (token.trim().equals(dia)) {
                return true;
            }
        }
        return false;
    }
}
