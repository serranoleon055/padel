package com.padel.rankpadel.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.HorarioCanchaRequest;
import com.padel.rankpadel.dto.response.HorarioCanchaResponse;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.HorarioCancha;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.HorarioCanchaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HorarioCanchaService {

    private final HorarioCanchaRepository horarioCanchaRepository;
    private final CanchaRepository canchaRepository;

    @Transactional
    public HorarioCanchaResponse guardar(HorarioCanchaRequest request) {
        validarHoras(request);
        Cancha cancha = canchaRepository.findById(request.getCanchaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cancha", request.getCanchaId()));
        return aResponse(aplicarHorario(cancha, request));
    }

    @Transactional
    public List<HorarioCanchaResponse> guardarParaSucursal(Long lugarId, HorarioCanchaRequest request) {
        validarHoras(request);
        List<Cancha> canchas = canchaRepository.findByLugarIdAndActivoTrue(lugarId);
        if (canchas.isEmpty()) {
            throw new ResourceNotFoundException("Lugar", lugarId);
        }

        List<HorarioCanchaResponse> respuestas = new ArrayList<>();
        for (Cancha cancha : canchas) {
            respuestas.add(aResponse(aplicarHorario(cancha, request)));
        }
        return respuestas;
    }

    private void validarHoras(HorarioCanchaRequest request) {
        if (request.getHoraApertura().equals(request.getHoraCierre())) {
            throw new EstadoInvalidoException("La hora de apertura y la de cierre no pueden ser iguales");
        }
    }

    private HorarioCancha aplicarHorario(Cancha cancha, HorarioCanchaRequest request) {
        List<HorarioCancha> previos = horarioCanchaRepository.findByCanchaId(cancha.getId());
        for (HorarioCancha previo : previos) {
            previo.setActivo(false);
        }
        horarioCanchaRepository.saveAll(previos);

        HorarioCancha horario = HorarioCancha.builder()
                .cancha(cancha)
                .horaApertura(request.getHoraApertura())
                .horaCierre(request.getHoraCierre())
                .diasActivos(request.getDiasActivos())
                .duracionesOfrecidas(normalizarDuraciones(request.getDuracionesOfrecidas()))
                .anticipacionDias(request.getAnticipacionDias() != null ? request.getAnticipacionDias() : 14)
                .activo(true)
                .build();
        return horarioCanchaRepository.save(horario);
    }

    @Transactional(readOnly = true)
    public List<HorarioCanchaResponse> listarPorCancha(Long canchaId) {
        return horarioCanchaRepository.findByCanchaId(canchaId).stream()
                .map(this::aResponse)
                .toList();
    }

    /**
     * Duraciones que el club vende, en minutos. Se filtran a múltiplos de la
     * granularidad de la agenda: una de 45 minutos no se podría dibujar ni reservar.
     */
    private String normalizarDuraciones(String crudo) {
        if (crudo == null || crudo.isBlank()) {
            return "60,120";
        }
        List<Integer> validas = new ArrayList<>();
        for (String token : crudo.split(",")) {
            try {
                int minutos = Integer.parseInt(token.trim());
                if (minutos > 0 && minutos % DisponibilidadCanchaService.GRANULARIDAD_MIN == 0
                        && !validas.contains(minutos)) {
                    validas.add(minutos);
                }
            } catch (NumberFormatException ignorada) {
                // Se descarta en silencio: abajo hay un valor por defecto usable.
            }
        }
        if (validas.isEmpty()) {
            throw new EstadoInvalidoException("Elegí al menos una duración de turno (60, 90 o 120 minutos).");
        }
        Collections.sort(validas);
        return validas.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private HorarioCanchaResponse aResponse(HorarioCancha horario) {
        return HorarioCanchaResponse.builder()
                .id(horario.getId())
                .canchaId(horario.getCancha() != null ? horario.getCancha().getId() : null)
                .horaApertura(horario.getHoraApertura())
                .horaCierre(horario.getHoraCierre())
                .diasActivos(horario.getDiasActivos())
                .duracionesOfrecidas(horario.getDuracionesOfrecidas())
                .anticipacionDias(horario.getAnticipacionDias())
                .activo(horario.isActivo())
                .build();
    }
}
