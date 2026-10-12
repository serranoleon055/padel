package com.padel.rankpadel.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EntregaPremioRequest {

    private boolean entregado;

    @Size(max = 255)
    private String entregadoA;
}
