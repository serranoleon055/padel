package com.padel.rankpadel.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.padel.rankpadel.enums.CategoriaGastoTorneo;
import com.padel.rankpadel.enums.MedioPago;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GastoTorneoRequest {

    @NotNull(message = "Indicá la fecha")
    private LocalDate fecha;

    @NotNull(message = "Elegí en qué fue el gasto")
    private CategoriaGastoTorneo categoria;

    @Size(max = 255)
    private String descripcion;

    @NotNull(message = "Indicá el monto")
    @DecimalMin(value = "0.01", message = "El monto tiene que ser mayor a cero")
    private BigDecimal monto;

    @NotNull(message = "Indicá cómo se pagó")
    private MedioPago medio;
}
