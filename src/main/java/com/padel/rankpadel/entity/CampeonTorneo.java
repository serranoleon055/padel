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
 * Campeón (y subcampeón) de una categoría de un torneo finalizado.
 * Fuente única de verdad para mostrar campeones, funcione el torneo por
 * eliminación (ganador de la final) o por liga/grupos (líder de la tabla),
 * casos en los que no existe un partido "Final".
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "campeones_torneo")
public class CampeonTorneo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "torneo_id")
    private Torneo torneo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pareja_campeona_id")
    private Pareja parejaCampeona;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pareja_subcampeona_id")
    private Pareja parejaSubcampeona;

    private String marcadorFinal;

    private LocalDateTime fechaCoronacion;

}
