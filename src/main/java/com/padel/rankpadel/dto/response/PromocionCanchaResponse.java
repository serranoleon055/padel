package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
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
public class PromocionCanchaResponse {

    private Long id;
    private Long canchaId;
    private String canchaNombre;
    private String nombre;
    private String diasSemana;
    private LocalTime horaDesde;
    private LocalTime horaHasta;
    private BigDecimal precioPorHora;
    private LocalDate vigenteDesde;
    private LocalDate vigenteHasta;

    /** La promoción está corriendo hoy: es lo que el club mira de un vistazo. */
    private boolean vigente;

    private boolean activo;
}
