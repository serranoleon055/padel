package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;

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
public class CanchaResponse {

    private Long id;
    private String nombre;
    private String descripcion;
    private boolean activo;
    private Long lugarId;
    private String lugarNombre;
    private BigDecimal precioPorHora;
    private Integer seniaPorcentaje;
    private boolean seniaObligatoria;

}
