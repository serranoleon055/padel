package com.padel.rankpadel.dto.request;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PropuestaFixtureRequest {

    /** No se programa nada antes. Null = desde ahora (o desde el inicio del torneo, si es después). */
    private LocalDateTime desde;

    /** Volver a ubicar también los partidos sin jugar que ya tenían horario desde {@link #desde}. */
    private boolean reprogramar;

    /** Solo los partidos de esta categoría. Null = todas. */
    private Long categoriaId;
}
