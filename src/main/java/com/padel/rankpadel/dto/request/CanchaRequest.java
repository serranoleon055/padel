package com.padel.rankpadel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CanchaRequest {

    @NotBlank(message = "El nombre de la cancha es obligatorio")
    private String nombre;

    private String descripcion;

    @NotNull(message = "Debe indicar el lugar al que pertenece la cancha")
    private Long lugarId;

}
