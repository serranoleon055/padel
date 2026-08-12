package com.padel.rankpadel.entity;

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

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "horarios_cancha")
public class HorarioCancha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancha_id")
    private Cancha cancha;

    private LocalTime horaApertura;
    private LocalTime horaCierre;
    private String diasActivos;

    /**
     * Duraciones que el club vende, en minutos, separadas por coma (ej. "60,90,120").
     * Sumar una duración agrega una opción de venta, no horarios nuevos: los turnos
     * arrancan siempre en hora en punto, incluso los de 90 minutos.
     */
    @Builder.Default
    private String duracionesOfrecidas = "60,120";

    private int anticipacionDias;

    @Builder.Default
    private boolean activo = true;
}
