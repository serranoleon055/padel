package com.padel.rankpadel.dto.request;

import com.padel.rankpadel.enums.FormatoTorneo;
import com.padel.rankpadel.enums.TipoSorteo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class PlantillaFormatoRequest {

    @NotBlank
    private String nombre;

    private String descripcion;

    @NotNull
    private FormatoTorneo formatoTorneo;

    @NotNull
    private TipoSorteo tipoSorteo;

    @Min(2)
    private Integer cantidadParejasObjetivo;

    @Min(1)
    private Integer cantidadGrupos;

    @Min(2)
    private Integer parejasPorGrupo;

    @Min(1)
    private Integer avanzanPorGrupo;

    private boolean incluyeFaseGrupos;
    private boolean incluyeEliminacion;

    @Builder.Default
    private boolean activo = true;
}
