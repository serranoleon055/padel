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
public class PagoInscripcionRequest {

    @NotNull
    private Long torneoId;

    @NotNull
    @Valid
    private SolicitudInscripcionRequest inscripcion;
}
