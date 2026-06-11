package com.padel.rankpadel.dto.response;

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
public class HorarioCanchaResponse {

    private Long id;
    private Long canchaId;
    private LocalTime horaApertura;
    private LocalTime horaCierre;
    private String diasActivos;
    private int duracionSlotMin;
    private int anticipacionDias;
    private boolean activo;
}
