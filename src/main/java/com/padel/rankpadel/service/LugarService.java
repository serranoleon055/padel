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
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.LugarRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LugarService {

    private final LugarRepository lugarRepository;
    private final LugarMapper lugarMapper;
    private final CanchaRepository canchaRepository;

    /**
     * Archivar una sede tiene que bajar también sus canchas.
     *
     * <p>Antes solo se marcaba el lugar: la sede desaparecía del selector y de la página
     * de precios, pero sus canchas seguían activas, se listaban con las buenas y se les
     * podían seguir cargando turnos. Quedaba una sede fantasma vendiendo horarios.
     *
     * <p>No se tocan las reservas ya hechas: la cancha deja de ofrecerse, el historial
     * queda como está.
     */
    private void archivar(Lugar lugar) {
        lugar.setArchivado(true);
        canchaRepository.findByLugarIdAndActivoTrue(lugar.getId())
                .forEach(cancha -> cancha.setActivo(false));
    }

    public List<LugarResponse> listarTodos() {
        return listarTodos(false);
    }

    /**
     * Con {@code incluirArchivadas} el panel ve también las sedes dadas de baja, que es lo
     * único que permite volver a activarlas: archivar era de ida y la única salida era
     * tocar la base a mano.
     */
    public List<LugarResponse> listarTodos(boolean incluirArchivadas) {
        return (incluirArchivadas ? lugarRepository.findAll() : lugarRepository.findByArchivadoFalse())
                .stream()
                .map(lugarMapper::lugarToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Reactiva la sede y vuelve a habilitar sus canchas, espejando lo que apaga
     * {@link #archivar}: dejarla activa con las canchas apagadas sería una sede que no
     * vende nada, que es justo el estado inconsistente que el archivado vino a evitar.
     */
    @Transactional
    public LugarResponse reactivar(Long id) {
        Lugar lugar = lugarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lugar", id));
        lugar.setArchivado(false);
        canchaRepository.findByLugarId(id).forEach(cancha -> cancha.setActivo(true));
        lugarRepository.save(lugar);
        return lugarMapper.lugarToResponse(lugar);
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
        archivar(lugar);
        lugarRepository.save(lugar);
    }

    @Transactional
    public void eliminarBatch(List<Long> ids) {
        List<Lugar> lugares = lugarRepository.findAllById(ids);
        lugares.forEach(this::archivar);
        lugarRepository.saveAll(lugares);
    }
}
