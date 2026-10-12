package com.padel.rankpadel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AvisoEnviadoRequest {

    @NotNull
    private Long jugadorId;

    @NotBlank
    @Size(max = 60)
    private String tema;

    @NotBlank
    @Size(max = 64)
    private String clave;
}
