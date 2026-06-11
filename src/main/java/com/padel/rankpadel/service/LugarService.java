package com.padel.rankpadel.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public List<LugarResponse> listarTodos() {
        return lugarRepository.findByArchivadoFalse()
                .stream()
                .map(lugarMapper::lugarToResponse)
                .collect(Collectors.toList());
    }

    public LugarResponse buscarPorId(Long id) {
        Lugar lugar = lugarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lugar", id));
        return lugarMapper.lugarToResponse(lugar);
    }

    @Transactional
    public LugarResponse crear(LugarRequest lugarRequest) {
        Lugar lugar = lugarMapper.requestToLugar(lugarRequest);
        lugarRepository.save(lugar);
        return lugarMapper.lugarToResponse(lugar);
    }

    @Transactional
    public LugarResponse actualizar(Long id, LugarRequest lugarRequest) {
        Lugar lugar = lugarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lugar", id));
        lugar.setNombre(lugarRequest.getNombre());
        lugar.setDireccion(lugarRequest.getDireccion());
        lugar.setCantidadCanchas(lugarRequest.getCantidadCanchas());
        lugarRepository.save(lugar);
        return lugarMapper.lugarToResponse(lugar);
    }

    @Transactional
    public void eliminar(Long id) {
        Lugar lugar = lugarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lugar", id));
        lugar.setArchivado(true);
        lugarRepository.save(lugar);
    }

    @Transactional
    public void eliminarBatch(List<Long> ids) {
        List<Lugar> lugares = lugarRepository.findAllById(ids);
        lugares.forEach(lugar -> lugar.setArchivado(true));
        lugarRepository.saveAll(lugares);
    }
}
