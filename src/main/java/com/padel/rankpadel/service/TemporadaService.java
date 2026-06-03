package com.padel.rankpadel.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.TemporadaRequest;
import com.padel.rankpadel.dto.response.TemporadaResponse;
import com.padel.rankpadel.entity.Temporada;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.TemporadaMapper;
import com.padel.rankpadel.repository.TemporadaRepository;
import com.padel.rankpadel.repository.TorneoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TemporadaService {

    private final TemporadaRepository temporadaRepository;
    private final TemporadaMapper temporadaMapper;
    private final TorneoRepository torneoRepository;

    public List<TemporadaResponse> listarTodos() {
        return temporadaRepository.findAll()
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
        temporadaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Temporada", id));

        Temporada temporadaActualizada = temporadaMapper.requestToTemporada(temporadaRequest);
        temporadaActualizada.setId(id);
        temporadaRepository.save(temporadaActualizada);

        return temporadaMapper.temporadaToResponse(temporadaActualizada);
    }

    @Transactional
    public void eliminar(Long id) {
        Temporada temporada = temporadaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Temporada", id));

        long torneosAsociados = torneoRepository.findByTemporada(temporada).size();
        if (torneosAsociados > 0) {
            throw new EstadoInvalidoException(
                    "No se puede eliminar la temporada \"" + temporada.getNombre() +
                            "\" porque tiene " + torneosAsociados + " torneo(s) asociado(s). " +
                            "Primero reasigná o eliminá esos torneos.");
        }

        temporadaRepository.delete(temporada);
    }

    @Transactional
    public void eliminarBatch(List<Long> ids) {
        List<Temporada> temporadas = temporadaRepository.findAllById(ids);
        temporadaRepository.deleteAll(temporadas);
    }
}