package com.padel.rankpadel.dto.request;

import com.padel.rankpadel.enums.Genero;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class CategoriaRequest {

    @NotBlank
    private String nombre;

    @Min(1)
    @Max(8)
    private int nivel;

    private Integer edadMin;
    private Integer edadMax;

    @NotNull
    private Genero genero;

}
