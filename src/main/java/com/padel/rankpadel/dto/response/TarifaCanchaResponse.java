package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalTime;

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
public class TarifaCanchaResponse {

    private Long id;
    private Long canchaId;
    private String canchaNombre;
    private String nombre;
    private String diasSemana;
    private LocalTime horaDesde;
    private LocalTime horaHasta;
    private BigDecimal precioPorHora;
    private boolean activo;
}
