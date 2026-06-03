package com.padel.rankpadel.dto.request;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
public class PlantillaPuntosRequest {

    @NotBlank
    private String nombre;

    private String descripcion;

    @Builder.Default
    private boolean activo = true;

    @Valid
    @NotEmpty
    @Builder.Default
    private List<PlantillaPuntosRondaRequest> rondas = new ArrayList<>();
}
