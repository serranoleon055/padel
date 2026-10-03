package com.padel.rankpadel.dto.request;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Abrir una planilla de conteo. */
@Getter
@Setter
@NoArgsConstructor
public class RecuentoRequest {

    @NotNull(message = "Indicá la fecha del conteo")
    private LocalDate fecha;

    /**
     * Qué productos entran a la planilla. Vacío o null = todos los que llevan stock, que
     * es el caso normal: un recuento parcial sirve para la heladera un martes cualquiera,
     * pero el conteo de fin de mes es de todo.
     */
    private List<Long> productoIds;

    @Size(max = 300)
    private String notas;
}
