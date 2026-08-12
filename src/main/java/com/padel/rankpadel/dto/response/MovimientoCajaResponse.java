package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un cobro tal como lo vivió el cliente. Un turno de dos horas son dos reservas y por lo
 * tanto dos cobros, pero en el mostrador se pagó una sola vez: acá van juntos, con el
 * horario completo del turno y el total.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MovimientoCajaResponse {

    /** Cobros que componen el movimiento. Anularlo es anularlos a todos. */
    private List<Long> cobroIds;

    private String clienteNombre;
    private String canchaNombre;

    /** Día y horario del turno cobrado, que puede no ser el día en que se cobró. */
    private LocalDate fechaTurno;
    private LocalTime horaInicio;
    private LocalTime horaFin;

    private BigDecimal monto;
    private String medio;
    private LocalDateTime cobradoEn;
    private String registradoPor;
    private String notas;
}
