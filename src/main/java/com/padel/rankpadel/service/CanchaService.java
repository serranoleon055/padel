package com.padel.rankpadel.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.CanchaRequest;
import com.padel.rankpadel.dto.response.CanchaResponse;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.Lugar;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.LugarRepository;
import com.padel.rankpadel.util.OrdenCanchas;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CanchaService {

    private final CanchaRepository canchaRepository;
    private final LugarRepository lugarRepository;

    @Transactional(readOnly = true)
    public List<CanchaResponse> listarTodas() {
        return canchaRepository.findByActivoTrue()
                .stream().map(this::toResponse)
                .sorted(OrdenCanchas.porNombre(CanchaResponse::getNombre))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CanchaResponse> listarPorLugar(Long lugarId) {
        return listarPorLugar(lugarId, false);
    }

    /**
     * Con {@code incluirBajas} se ven también las canchas dadas de baja. Es lo que permite
     * volver a habilitar la que se apagó por una refacción: sin verla en el listado no
     * había forma de reactivarla desde la pantalla.
     */
    @Transactional(readOnly = true)
    public List<CanchaResponse> listarPorLugar(Long lugarId, boolean incluirBajas) {
        return (incluirBajas ? canchaRepository.findByLugarId(lugarId)
                : canchaRepository.findByLugarIdAndActivoTrue(lugarId))
                .stream().map(this::toResponse)
                .sorted(OrdenCanchas.porNombre(CanchaResponse::getNombre))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CanchaResponse buscarPorId(Long id) {
        return toResponse(canchaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cancha", id)));
    }

    @Transactional
    public CanchaResponse crear(CanchaRequest request) {
        Lugar lugar = lugarRepository.findById(request.getLugarId())
                .orElseThrow(() -> new ResourceNotFoundException("Lugar", request.getLugarId()));
        Cancha cancha = Cancha.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .lugar(lugar)
                .precioPorHora(request.getPrecioPorHora())
                .seniaPorcentaje(request.getSeniaPorcentaje())
                .seniaObligatoria(request.isSeniaObligatoria())
                .build();
        return toResponse(canchaRepository.save(cancha));
    }

    @Transactional
    public CanchaResponse actualizar(Long id, CanchaRequest request) {
        Cancha cancha = canchaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cancha", id));
        Lugar lugar = lugarRepository.findById(request.getLugarId())
                .orElseThrow(() -> new ResourceNotFoundException("Lugar", request.getLugarId()));
        cancha.setNombre(request.getNombre());
        cancha.setDescripcion(request.getDescripcion());
        cancha.setLugar(lugar);
        cancha.setPrecioPorHora(request.getPrecioPorHora());
        cancha.setSeniaPorcentaje(request.getSeniaPorcentaje());
        cancha.setSeniaObligatoria(request.isSeniaObligatoria());
        return toResponse(canchaRepository.save(cancha));
    }

    @Transactional
    public void eliminar(Long id) {
        Cancha cancha = canchaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cancha", id));
        cancha.setActivo(false);
        canchaRepository.save(cancha);
    }

    /** Vuelve a poner la cancha en venta. Los turnos ya cargados no se tocan. */
    @Transactional
    public CanchaResponse reactivar(Long id) {
        Cancha cancha = canchaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cancha", id));
        cancha.setActivo(true);
        return toResponse(canchaRepository.save(cancha));
    }

    private CanchaResponse toResponse(Cancha c) {
        return CanchaResponse.builder()
                .id(c.getId())
                .nombre(c.getNombre())
                .descripcion(c.getDescripcion())
                .activo(c.isActivo())
                .lugarId(c.getLugar() != null ? c.getLugar().getId() : null)
                .lugarNombre(c.getLugar() != null ? c.getLugar().getNombre() : null)
                .precioPorHora(c.getPrecioPorHora())
                .seniaPorcentaje(c.getSeniaPorcentaje())
                .seniaObligatoria(c.isSeniaObligatoria())
                .build();
    }

}
