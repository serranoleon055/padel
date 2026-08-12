package com.padel.rankpadel.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Qué va a pasar (o qué pasó) con un archivo importado.
 *
 * <p>Se devuelve igual en la vista previa y en la importación real, así el club ve
 * exactamente lo mismo antes y después: si la previa dice "12 nuevos, 3 repetidos", eso
 * es lo que va a quedar.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ImportacionResponse {

    /** true = todavía no se guardó nada. */
    private boolean vistaPrevia;

    private int filasLeidas;
    private int nuevos;
    /** Ya existían: se reconocieron por teléfono normalizado o por nombre. */
    private int repetidos;
    private int conError;

    /** Detalle fila por fila, para que el club vea qué se va a hacer con cada una. */
    private List<Fila> filas;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Fila {
        private int numero;
        private String nombre;
        private String telefono;
        /** NUEVO, REPETIDO o ERROR. */
        private String resultado;
        /** Por qué se saltea o con quién choca. Null si entra limpio. */
        private String detalle;
    }
}
