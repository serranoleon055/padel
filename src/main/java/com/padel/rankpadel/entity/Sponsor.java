package com.padel.rankpadel.entity;

import java.time.LocalDateTime;

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
 * Un auspiciante del club, con su logo en las páginas públicas.
 *
 * <p>Es lo que convierte al sistema en algo que le da plata al club en lugar de costarle:
 * el espacio del cuadro del torneo se vende, y ese ingreso cubre el abono.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "sponsors")
public class Sponsor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    /** Sube por el mismo camino que las fotos de la galería de la sede. */
    private String logoUrl;

    /** A dónde lleva el logo. Opcional: no todo sponsor tiene web. */
    private String enlace;

    /** Null = se muestra en todas las sedes. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lugar_id")
    private Lugar lugar;

    /** El club decide quién va primero: el que más paga, arriba. */
    @Builder.Default
    private int orden = 0;

    @Builder.Default
    private boolean activo = true;

    private LocalDateTime creadoEn;
}
