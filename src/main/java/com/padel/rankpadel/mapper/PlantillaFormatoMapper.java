package com.padel.rankpadel.mapper;

import org.springframework.stereotype.Component;

import com.padel.rankpadel.dto.request.PlantillaFormatoRequest;
import com.padel.rankpadel.dto.response.PlantillaFormatoResponse;
import com.padel.rankpadel.entity.PlantillaFormato;

@Component
public class PlantillaFormatoMapper {

    public PlantillaFormato requestToPlantilla(PlantillaFormatoRequest request) {
        return PlantillaFormato.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .formatoTorneo(request.getFormatoTorneo())
                .tipoSorteo(request.getTipoSorteo())
                .cantidadParejasObjetivo(request.getCantidadParejasObjetivo())
                .cantidadGrupos(request.getCantidadGrupos())
                .parejasPorGrupo(request.getParejasPorGrupo())
                .avanzanPorGrupo(request.getAvanzanPorGrupo())
                .incluyeFaseGrupos(request.isIncluyeFaseGrupos())
                .incluyeEliminacion(request.isIncluyeEliminacion())
                .activo(request.isActivo())
                .build();
    }

    public PlantillaFormatoResponse plantillaToResponse(PlantillaFormato plantilla) {
        return PlantillaFormatoResponse.builder()
                .id(plantilla.getId())
                .nombre(plantilla.getNombre())
                .descripcion(plantilla.getDescripcion())
                .formatoTorneo(plantilla.getFormatoTorneo())
                .tipoSorteo(plantilla.getTipoSorteo())
                .cantidadParejasObjetivo(plantilla.getCantidadParejasObjetivo())
                .cantidadGrupos(plantilla.getCantidadGrupos())
                .parejasPorGrupo(plantilla.getParejasPorGrupo())
                .avanzanPorGrupo(plantilla.getAvanzanPorGrupo())
                .incluyeFaseGrupos(plantilla.isIncluyeFaseGrupos())
                .incluyeEliminacion(plantilla.isIncluyeEliminacion())
                .activo(plantilla.isActivo())
                .build();
    }

    public void actualizarEntidad(PlantillaFormato plantilla, PlantillaFormatoRequest request) {
        plantilla.setNombre(request.getNombre());
        plantilla.setDescripcion(request.getDescripcion());
        plantilla.setFormatoTorneo(request.getFormatoTorneo());
        plantilla.setTipoSorteo(request.getTipoSorteo());
        plantilla.setCantidadParejasObjetivo(request.getCantidadParejasObjetivo());
        plantilla.setCantidadGrupos(request.getCantidadGrupos());
        plantilla.setParejasPorGrupo(request.getParejasPorGrupo());
        plantilla.setAvanzanPorGrupo(request.getAvanzanPorGrupo());
        plantilla.setIncluyeFaseGrupos(request.isIncluyeFaseGrupos());
        plantilla.setIncluyeEliminacion(request.isIncluyeEliminacion());
        plantilla.setActivo(request.isActivo());
    }
}
