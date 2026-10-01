package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * El turno visto por el jugador que abre su enlace.
 *
 * <p>Es a propósito más chico que {@code ReservaResponse}: acá no viajan ni el id interno,
 * ni el teléfono, ni la ficha del cliente. Lo abre cualquiera que tenga el enlace, así que
 * solo lleva lo que la persona necesita para reconocer su turno y decidir si lo cancela.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TurnoPublicoResponse {

    /** El corto, el que se dice por teléfono si hay que llamar al club. */
    private String codigo;
    private String clienteNombre;
    private String canchaNombre;
    private String lugarNombre;
    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String estado;

    private BigDecimal precio;
    /** Seña ya acreditada por Mercado Pago. Cero si el turno se paga entero en el club. */
    private BigDecimal seniaPagada;
    private BigDecimal saldoPendiente;

    /** Si el botón de cancelar tiene que estar habilitado. */
    private boolean puedeCancelar;
    /**
     * Por qué no se puede cancelar, en palabras que se le puedan mostrar a la persona.
     * Null cuando sí se puede.
     */
    private String motivoNoCancelable;
    /** La ventana que configuró el club, para poder explicarla en pantalla. */
    private int horasMinimasCancelacion;
}
