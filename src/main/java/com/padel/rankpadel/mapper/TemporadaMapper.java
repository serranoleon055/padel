package com.padel.rankpadel.mapper;

import org.springframework.stereotype.Component;

import com.padel.rankpadel.dto.request.TemporadaRequest;
import com.padel.rankpadel.dto.response.TemporadaResponse;
import com.padel.rankpadel.entity.Temporada;

@Component
public class TemporadaMapper {

    public Temporada requestToTemporada(TemporadaRequest temporadaRequest) {

        Temporada temporada = Temporada.builder()
                .nombre(temporadaRequest.getNombre())
                .fechaInicio(temporadaRequest.getFechaInicio())
                .fechaFin(temporadaRequest.getFechaFin())
                .activa(temporadaRequest.isActiva())
                .build();

        return temporada;
    }

    public TemporadaResponse temporadaToResponse(Temporada temporada) {

        TemporadaResponse temporadaDTO = TemporadaResponse.builder()
                .id(temporada.getId())
                .nombre(temporada.getNombre())
                .fechaInicio(temporada.getFechaInicio())
                .fechaFin(temporada.getFechaFin())
                .activa(temporada.isActiva())
                .build();

        return temporadaDTO;
    }

}
