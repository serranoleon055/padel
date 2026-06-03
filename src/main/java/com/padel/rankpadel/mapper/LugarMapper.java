package com.padel.rankpadel.mapper;

import org.springframework.stereotype.Component;

import com.padel.rankpadel.dto.request.LugarRequest;
import com.padel.rankpadel.dto.response.LugarResponse;
import com.padel.rankpadel.entity.Lugar;

@Component
public class LugarMapper {

    public Lugar requestToLugar(LugarRequest lugarRequest) {

        Lugar lugar = Lugar.builder()
                .nombre(lugarRequest.getNombre())
                .direccion(lugarRequest.getDireccion())
                .cantidadCanchas(lugarRequest.getCantidadCanchas())
                .build();

        return lugar;
    }

    public LugarResponse lugarToResponse(Lugar lugar) {

        LugarResponse lugarDTO = LugarResponse.builder()
                .id(lugar.getId())
                .nombre(lugar.getNombre())
                .direccion(lugar.getDireccion())
                .cantidadCanchas(lugar.getCantidadCanchas())
                .build();

        return lugarDTO;
    }

}
