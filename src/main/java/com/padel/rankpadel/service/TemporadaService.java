package com.padel.rankpadel.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.TemporadaRequest;
import com.padel.rankpadel.dto.response.TemporadaResponse;
import com.padel.rankpadel.entity.Temporada;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.TemporadaMapper;
import com.padel.rankpadel.repository.TemporadaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TemporadaService {

    private final TemporadaRepository temporadaRepository;
    private final TemporadaMapper temporadaMapper;

    public List<TemporadaResponse> listarTodos() {
        return temporadaRepository.findByArchivadoFalse()
                .stream()
                .map(temporadaMapper::temporadaToResponse)
                .collect(Collectors.toList());
    }

    public TemporadaResponse buscarPorId(Long id) {
        Temporada temporada = temporadaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Temporada", id));
        return temporadaMapper.temporadaToResponse(temporada);
    }

    @Transactional
    public TemporadaResponse crear(TemporadaRequest temporadaRequest) {
        Temporada temporada = temporadaMapper.requestToTemporada(temporadaRequest);
        temporadaRepository.save(temporada);
        return temporadaMapper.temporadaToResponse(temporada);
    }

    @Transactional
    public TemporadaResponse actualizar(Long id, TemporadaRequest temporadaRequest) {
        Temporada temporada = temporadaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Temporada", id));
        temporada.setNombre(temporadaRequest.getNombre());
        temporada.setFechaInicio(temporadaRequest.getFechaInicio());
        temporada.setFechaFin(temporadaRequest.getFechaFin());
        temporada.setActiva(temporadaRequest.isActiva());
        temporadaRepository.save(temporada);
        return temporadaMapper.temporadaToResponse(temporada);
    }

    @Transactional
    public void eliminar(Long id) {
        Temporada temporada = temporadaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Temporada", id));
        temporada.setArchivado(true);
        temporadaRepository.save(temporada);
    }

    @Transactional
    public void eliminarBatch(List<Long> ids) {
        List<Temporada> temporadas = temporadaRepository.findAllById(ids);
        temporadas.forEach(temporada -> temporada.setArchivado(true));
        temporadaRepository.saveAll(temporadas);
    }
}
