package com.padel.rankpadel.entity;

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
@Table(name = "plantillas_puntos_rondas")
public class PlantillaPuntosRonda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombreRonda;
    private int puntosGanador;
    private int puntosPerdedor;
    private int orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plantilla_id")
    private PlantillaPuntos plantilla;
}
