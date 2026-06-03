package com.padel.rankpadel.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlantillaPuntosResponse {

    private Long id;
    private String nombre;
    private String descripcion;
    private boolean activo;
    private List<PlantillaPuntosRondaResponse> rondas;
}
