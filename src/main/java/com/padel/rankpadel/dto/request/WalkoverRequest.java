package com.padel.rankpadel.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WalkoverRequest {

    @NotNull(message = "Debe indicar el ID de la pareja ganadora")
    private Long ganadorParejaId;

    @NotNull(message = "Debe indicar el tipo: WALKOVER o RETIRO")
    private String tipo;

    private String motivo;

}
