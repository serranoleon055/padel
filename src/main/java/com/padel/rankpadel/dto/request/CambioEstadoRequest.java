package com.padel.rankpadel.dto.request;

import com.padel.rankpadel.enums.EstadoTorneo;

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
public class CambioEstadoRequest {

    @NotNull
    private EstadoTorneo estado;

}
