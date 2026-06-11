package com.padel.rankpadel.service;

import java.util.List;

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
        Cancha cancha = canchaRepository.findById(request.getCanchaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cancha", request.getCanchaId()));

        if (request.getHoraApertura().equals(request.getHoraCierre())) {
            throw new EstadoInvalidoException("La hora de apertura y la de cierre no pueden ser iguales");
        }

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
                .duracionSlotMin(request.getDuracionSlotMin() != null ? request.getDuracionSlotMin() : 60)
                .anticipacionDias(request.getAnticipacionDias() != null ? request.getAnticipacionDias() : 14)
                .activo(true)
                .build();
        horarioCanchaRepository.save(horario);
        return aResponse(horario);
    }

    @Transactional(readOnly = true)
    public List<HorarioCanchaResponse> listarPorCancha(Long canchaId) {
        return horarioCanchaRepository.findByCanchaId(canchaId).stream()
                .map(this::aResponse)
                .toList();
    }

    private HorarioCanchaResponse aResponse(HorarioCancha horario) {
        return HorarioCanchaResponse.builder()
                .id(horario.getId())
                .canchaId(horario.getCancha() != null ? horario.getCancha().getId() : null)
                .horaApertura(horario.getHoraApertura())
                .horaCierre(horario.getHoraCierre())
                .diasActivos(horario.getDiasActivos())
                .duracionSlotMin(horario.getDuracionSlotMin())
                .anticipacionDias(horario.getAnticipacionDias())
                .activo(horario.isActivo())
                .build();
    }
}
