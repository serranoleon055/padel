package com.padel.rankpadel.dto.request;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProgramarPartidoRequest {

    @NotNull(message = "La fecha y hora programada es obligatoria")
    private LocalDateTime fechaHora;

    private Long canchaId;

}
