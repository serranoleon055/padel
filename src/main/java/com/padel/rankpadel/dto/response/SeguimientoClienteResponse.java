package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un cliente al que conviene escribirle, con el dato que explica por qué aparece en la
 * lista. El club ya tenía toda esta información cargada; lo que faltaba era que el sistema
 * se la pusiera adelante en vez de esperar a que alguien la fuera a buscar.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SeguimientoClienteResponse {

    private Long clienteId;
    private String nombre;
    private String telefono;
    private long turnos;
    private BigDecimal gastado;
    private LocalDate ultimoTurno;
    private LocalDate primerTurno;
    /** Días desde el último turno. Es el número que decide si vale la pena el mensaje. */
    private long diasSinVenir;
}
