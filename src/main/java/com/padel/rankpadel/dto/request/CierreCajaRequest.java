package com.padel.rankpadel.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Lo único que el club aporta al cerrar: qué día y cuánta plata había en el cajón. */
@Getter
@Setter
@NoArgsConstructor
public class CierreCajaRequest {

    @NotNull(message = "Indicá qué día estás cerrando")
    private LocalDate fecha;

    @NotNull(message = "Contá el efectivo del cajón antes de cerrar")
    @DecimalMin(value = "0.00", message = "El efectivo contado no puede ser negativo")
    private BigDecimal efectivoContado;

    @Size(max = 300)
    private String notas;
}
