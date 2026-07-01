package com.padel.rankpadel.dto.response;

import java.util.List;

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
public class ConfiguracionCategoriaTorneoResponse {

    private Long categoriaId;
    private String categoriaNombre;
    private FormatoTorneo formato;
    private Long plantillaFormatoId;
    private String plantillaFormatoNombre;
    private Long plantillaPuntosId;
    private String plantillaPuntosNombre;
    private Integer cantidadParejasObjetivo;
    private Integer cantidadGrupos;
    private Integer parejasPorGrupo;
    private Integer avanzanPorGrupo;
    private boolean incluyeFaseGrupos;
    private boolean incluyeEliminacion;
    private TipoSorteo tipoSorteo;
    private Integer mejorDeSets;
    private Integer cupo;
    private List<ConfiguracionPuntosResponse> configuracionPuntos;
}
