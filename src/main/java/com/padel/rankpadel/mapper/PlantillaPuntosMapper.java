package com.padel.rankpadel.mapper;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.padel.rankpadel.dto.request.PlantillaPuntosRequest;
import com.padel.rankpadel.dto.request.PlantillaPuntosRondaRequest;
import com.padel.rankpadel.dto.response.PlantillaPuntosResponse;
import com.padel.rankpadel.dto.response.PlantillaPuntosRondaResponse;
import com.padel.rankpadel.entity.PlantillaPuntos;
import com.padel.rankpadel.entity.PlantillaPuntosRonda;

@Component
public class PlantillaPuntosMapper {

    public PlantillaPuntos requestToPlantilla(PlantillaPuntosRequest request) {
        PlantillaPuntos plantilla = PlantillaPuntos.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .activo(request.isActivo())
                .build();
        plantilla.reemplazarRondas(mapearRondasRequest(request.getRondas()));
        return plantilla;
    }

    public PlantillaPuntosResponse plantillaToResponse(PlantillaPuntos plantilla) {
        return PlantillaPuntosResponse.builder()
                .id(plantilla.getId())
                .nombre(plantilla.getNombre())
                .descripcion(plantilla.getDescripcion())
                .activo(plantilla.isActivo())
                .rondas(mapearRondasResponse(plantilla.getRondas()))
                .build();
    }

    public void actualizarEntidad(PlantillaPuntos plantilla, PlantillaPuntosRequest request) {
        plantilla.setNombre(request.getNombre());
        plantilla.setDescripcion(request.getDescripcion());
        plantilla.setActivo(request.isActivo());
        plantilla.reemplazarRondas(mapearRondasRequest(request.getRondas()));
    }

    private List<PlantillaPuntosRonda> mapearRondasRequest(List<PlantillaPuntosRondaRequest> rondas) {
        return rondas.stream()
                .sorted(Comparator.comparing(PlantillaPuntosRondaRequest::getOrden))
                .map(ronda -> PlantillaPuntosRonda.builder()
                        .nombreRonda(ronda.getNombreRonda())
                        .puntosGanador(ronda.getPuntosGanador())
                        .puntosPerdedor(ronda.getPuntosPerdedor())
                        .orden(ronda.getOrden())
                        .build())
                .toList();
    }

    private List<PlantillaPuntosRondaResponse> mapearRondasResponse(List<PlantillaPuntosRonda> rondas) {
        if (rondas == null) {
            return List.of();
        }

        return rondas.stream()
                .sorted(Comparator.comparing(PlantillaPuntosRonda::getOrden))
                .map(ronda -> PlantillaPuntosRondaResponse.builder()
                        .id(ronda.getId())
                        .nombreRonda(ronda.getNombreRonda())
                        .puntosGanador(ronda.getPuntosGanador())
                        .puntosPerdedor(ronda.getPuntosPerdedor())
                        .orden(ronda.getOrden())
                        .build())
                .toList();
    }
}
