package com.padel.rankpadel.dto.request;

import jakarta.validation.Valid;
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
public class SolicitudInscripcionRequest {

    @NotNull
    private Long categoriaId;

    private String telefonoContacto;

    @NotNull
    @Valid
    private IntegranteInscripcionRequest jugador1;

    @NotNull
    @Valid
    private IntegranteInscripcionRequest jugador2;
}
