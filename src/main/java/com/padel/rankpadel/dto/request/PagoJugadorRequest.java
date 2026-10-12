package com.padel.rankpadel.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.padel.rankpadel.enums.MedioPago;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un pago de inscripción que se cobra a mano (efectivo, transferencia...). */
@Getter
@Setter
@NoArgsConstructor
public class PagoJugadorRequest {

    @NotNull(message = "Indicá qué jugador pagó")
    private Long jugadorId;

    @NotNull(message = "Indicá la fecha del pago")
    private LocalDate fecha;

    @NotNull(message = "Indicá el monto")
    @DecimalMin(value = "0.01", message = "El monto tiene que ser mayor a cero")
    private BigDecimal monto;

    @NotNull(message = "Indicá cómo pagó")
    private MedioPago medio;

    @Size(max = 255)
    private String notas;
}
