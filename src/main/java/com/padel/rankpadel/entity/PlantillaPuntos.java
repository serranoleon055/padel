package com.padel.rankpadel.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
@Table(name = "plantillas_puntos")
public class PlantillaPuntos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String descripcion;

    @Builder.Default
    private boolean activo = true;

    @Builder.Default
    @OneToMany(mappedBy = "plantilla", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<PlantillaPuntosRonda> rondas = new ArrayList<>();

    public void reemplazarRondas(List<PlantillaPuntosRonda> nuevasRondas) {
        rondas.clear();
        nuevasRondas.forEach(this::agregarRonda);
    }

    public void agregarRonda(PlantillaPuntosRonda ronda) {
        ronda.setPlantilla(this);
        rondas.add(ronda);
    }
}
