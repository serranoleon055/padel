package com.padel.rankpadel.dto.request;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MoverPartidoRequest {

    @NotNull(message = "Elegí la cancha")
    private Long canchaId;

    @NotNull(message = "Elegí el día y la hora")
    private LocalDateTime inicio;
}
