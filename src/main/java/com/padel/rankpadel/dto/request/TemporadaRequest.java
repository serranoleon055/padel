package com.padel.rankpadel.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
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
public class TemporadaRequest {

    @NotBlank
    private String nombre;

    @NotNull
    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private boolean activa;

}
