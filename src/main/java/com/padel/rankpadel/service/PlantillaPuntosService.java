package com.padel.rankpadel.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.PlantillaPuntosRequest;
import com.padel.rankpadel.dto.response.PlantillaPuntosResponse;
import com.padel.rankpadel.entity.PlantillaPuntos;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.PlantillaPuntosMapper;
import com.padel.rankpadel.repository.PlantillaPuntosRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlantillaPuntosService {

    private final PlantillaPuntosRepository plantillaPuntosRepository;
    private final PlantillaPuntosMapper plantillaPuntosMapper;

    @Transactional(readOnly = true)
    public List<PlantillaPuntosResponse> listarTodos(Boolean soloActivas) {
        List<PlantillaPuntos> plantillas = Boolean.TRUE.equals(soloActivas)
                ? plantillaPuntosRepository.findByActivoTrueOrderByNombreAsc()
                : plantillaPuntosRepository.findAllByOrderByNombreAsc();

        return plantillas.stream()
                .map(plantillaPuntosMapper::plantillaToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlantillaPuntosResponse buscarPorId(Long id) {
        PlantillaPuntos plantilla = plantillaPuntosRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PlantillaPuntos", id));
        return plantillaPuntosMapper.plantillaToResponse(plantilla);
    }

    @Transactional
    public PlantillaPuntosResponse crear(PlantillaPuntosRequest request) {
        PlantillaPuntos plantilla = plantillaPuntosMapper.requestToPlantilla(request);
        plantillaPuntosRepository.save(plantilla);
        return plantillaPuntosMapper.plantillaToResponse(plantilla);
    }

    @Transactional
    public PlantillaPuntosResponse actualizar(Long id, PlantillaPuntosRequest request) {
        PlantillaPuntos plantilla = plantillaPuntosRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PlantillaPuntos", id));
        plantillaPuntosMapper.actualizarEntidad(plantilla, request);
        plantillaPuntosRepository.save(plantilla);
        return plantillaPuntosMapper.plantillaToResponse(plantilla);
    }

    @Transactional
    public void eliminar(Long id) {
        PlantillaPuntos plantilla = plantillaPuntosRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PlantillaPuntos", id));
        plantilla.setActivo(false);
        plantillaPuntosRepository.save(plantilla);
    }
}
