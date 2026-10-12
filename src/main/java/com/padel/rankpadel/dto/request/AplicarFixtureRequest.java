package com.padel.rankpadel.dto.request;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AplicarFixtureRequest {

    @NotEmpty(message = "No hay horarios para aplicar")
    @Valid
    private List<Asignacion> asignaciones;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Asignacion {
        @NotNull
        private Long partidoId;
        @NotNull
        private Long canchaId;
        @NotNull
        private LocalDateTime inicio;
    }
}
