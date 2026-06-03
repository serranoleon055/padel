package com.padel.rankpadel.dto.response;

import com.padel.rankpadel.enums.FormatoTorneo;
import com.padel.rankpadel.enums.TipoSorteo;

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
public class PlantillaFormatoResponse {

    private Long id;
    private String nombre;
    private String descripcion;
    private FormatoTorneo formatoTorneo;
    private TipoSorteo tipoSorteo;
    private Integer cantidadParejasObjetivo;
    private Integer cantidadGrupos;
    private Integer parejasPorGrupo;
    private Integer avanzanPorGrupo;
    private boolean incluyeFaseGrupos;
    private boolean incluyeEliminacion;
    private boolean activo;
}
