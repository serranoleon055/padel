package com.padel.rankpadel.dto.request;

import java.util.ArrayList;
import java.util.List;

import com.padel.rankpadel.enums.FormatoTorneo;
import com.padel.rankpadel.enums.TipoSorteo;

import jakarta.validation.constraints.NotNull;
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
public class ConfiguracionCategoriaTorneoRequest {

    @NotNull
    private Long categoriaId;

    private FormatoTorneo formato;
    private Long plantillaFormatoId;
    private Long plantillaPuntosId;
    private Integer cantidadParejasObjetivo;
    private Integer cantidadGrupos;
    private Integer parejasPorGrupo;
    private Integer avanzanPorGrupo;
    private boolean incluyeFaseGrupos;
    private boolean incluyeEliminacion;
    private TipoSorteo tipoSorteo;
    private Integer mejorDeSets;
    private Integer cupo;

    @Builder.Default
    private List<ConfiguracionPuntosRequest> configuracionPuntos = new ArrayList<>();
}
