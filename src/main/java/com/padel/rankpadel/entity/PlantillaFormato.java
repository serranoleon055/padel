package com.padel.rankpadel.entity;

import com.padel.rankpadel.enums.FormatoTorneo;
import com.padel.rankpadel.enums.TipoSorteo;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "plantillas_formato")
public class PlantillaFormato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String descripcion;

    @Enumerated(EnumType.STRING)
    private FormatoTorneo formatoTorneo;

    @Enumerated(EnumType.STRING)
    private TipoSorteo tipoSorteo;

    private Integer cantidadParejasObjetivo;
    private Integer cantidadGrupos;
    private Integer parejasPorGrupo;
    private Integer avanzanPorGrupo;
    private boolean incluyeFaseGrupos;
    private boolean incluyeEliminacion;

    @Builder.Default
    private boolean activo = true;
}
