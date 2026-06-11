package com.padel.rankpadel.dto.request;

import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
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
public class HorarioCanchaRequest {

    @NotNull
    private Long canchaId;

    @NotNull
    private LocalTime horaApertura;

    @NotNull
    private LocalTime horaCierre;

    private String diasActivos;
    private Integer duracionSlotMin;
    private Integer anticipacionDias;
}
