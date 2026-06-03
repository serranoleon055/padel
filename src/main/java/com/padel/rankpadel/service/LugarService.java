package com.padel.rankpadel.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.TorneoRepository;
import com.padel.rankpadel.dto.request.LugarRequest;
import com.padel.rankpadel.dto.response.LugarResponse;
import com.padel.rankpadel.entity.Lugar;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.LugarMapper;
import com.padel.rankpadel.repository.LugarRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LugarService {

    private final LugarRepository lugarRepository;
    private final LugarMapper lugarMapper;
    private final TorneoRepository torneoRepository;

    public List<LugarResponse> listarTodos() {
        return lugarRepository.findAll()
                .stream()
                .map(lugarMapper::lugarToResponse)
                .collect(Collectors.toList());
    }

    public LugarResponse buscarPorId(Long id) {

        Lugar lugar = lugarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lugar", id));

        LugarResponse lugarDTO = lugarMapper.lugarToResponse(lugar);
        return lugarDTO;
    }

    @Transactional
    public LugarResponse crear(LugarRequest lugarRequest) {

        Lugar lugar = lugarMapper.requestToLugar(lugarRequest);
        lugarRepository.save(lugar);

        LugarResponse lugarDTO = lugarMapper.lugarToResponse(lugar);
        return lugarDTO;
    }

    @Transactional
    public LugarResponse actualizar(Long id, LugarRequest lugarRequest) {
        Lugar lugar = lugarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lugar", id));

        lugar = lugarMapper.requestToLugar(lugarRequest);
        lugar.setId(id);
        lugarRepository.save(lugar);

        LugarResponse lugarDTO = lugarMapper.lugarToResponse(lugar);
        return lugarDTO;
    }

    @Transactional
    public void eliminar(Long id) {
        Lugar lugar = lugarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lugar", id));

        long torneosAsociados = torneoRepository.findByLugar(lugar).size();
        if (torneosAsociados > 0) {
            throw new EstadoInvalidoException(
                    "No se puede eliminar el lugar \"" + lugar.getNombre() +
                            "\" porque tiene " + torneosAsociados + " torneo(s) asociado(s). " +
                            "Primero reasigná o eliminá esos torneos.");
        }

        lugarRepository.delete(lugar);
    }

    @Transactional
    public void eliminarBatch(List<Long> ids) {
        List<Lugar> lugares = lugarRepository.findAllById(ids);
        lugarRepository.deleteAll(lugares);
    }

}
