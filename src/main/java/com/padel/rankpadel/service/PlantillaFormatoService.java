package com.padel.rankpadel.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.PlantillaFormatoRequest;
import com.padel.rankpadel.dto.response.PlantillaFormatoResponse;
import com.padel.rankpadel.entity.PlantillaFormato;
import com.padel.rankpadel.enums.FormatoTorneo;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.PlantillaFormatoMapper;
import com.padel.rankpadel.repository.PlantillaFormatoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlantillaFormatoService {

    private final PlantillaFormatoRepository plantillaFormatoRepository;
    private final PlantillaFormatoMapper plantillaFormatoMapper;

    @Transactional(readOnly = true)
    public List<PlantillaFormatoResponse> listarTodos(Boolean soloActivas) {
        List<PlantillaFormato> plantillas = Boolean.TRUE.equals(soloActivas)
                ? plantillaFormatoRepository.findByActivoTrueOrderByNombreAsc()
                : plantillaFormatoRepository.findAllByOrderByNombreAsc();

        return plantillas.stream()
                .map(plantillaFormatoMapper::plantillaToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlantillaFormatoResponse buscarPorId(Long id) {
        PlantillaFormato plantilla = plantillaFormatoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PlantillaFormato", id));
        return plantillaFormatoMapper.plantillaToResponse(plantilla);
    }

    @Transactional
    public PlantillaFormatoResponse crear(PlantillaFormatoRequest request) {
        normalizarYValidar(request);
        PlantillaFormato plantilla = plantillaFormatoMapper.requestToPlantilla(request);
        plantillaFormatoRepository.save(plantilla);
        return plantillaFormatoMapper.plantillaToResponse(plantilla);
    }

    @Transactional
    public PlantillaFormatoResponse actualizar(Long id, PlantillaFormatoRequest request) {
        PlantillaFormato plantilla = plantillaFormatoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PlantillaFormato", id));
        normalizarYValidar(request);
        plantillaFormatoMapper.actualizarEntidad(plantilla, request);
        plantillaFormatoRepository.save(plantilla);
        return plantillaFormatoMapper.plantillaToResponse(plantilla);
    }

    @Transactional
    public void eliminar(Long id) {
        PlantillaFormato plantilla = plantillaFormatoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PlantillaFormato", id));
        plantilla.setActivo(false);
        plantillaFormatoRepository.save(plantilla);
    }

    private void normalizarYValidar(PlantillaFormatoRequest request) {
        if (request.getFormatoTorneo() == FormatoTorneo.ELIMINACION_DIRECTA) {
            request.setIncluyeFaseGrupos(false);
            request.setIncluyeEliminacion(true);
        }

        if (!request.isIncluyeFaseGrupos() && !request.isIncluyeEliminacion()) {
            throw new EstadoInvalidoException("La plantilla debe incluir fase de grupos o eliminación");
        }

        if (!request.isIncluyeFaseGrupos()) {
            request.setCantidadGrupos(null);
            request.setParejasPorGrupo(null);
            request.setAvanzanPorGrupo(null);
            return;
        }

        if (!request.isIncluyeEliminacion()) {
            request.setAvanzanPorGrupo(null);
        }

        if (request.isIncluyeEliminacion() && request.getAvanzanPorGrupo() == null) {
            throw new EstadoInvalidoException("Indicá cuántas parejas avanzan por grupo");
        }
    }
}
