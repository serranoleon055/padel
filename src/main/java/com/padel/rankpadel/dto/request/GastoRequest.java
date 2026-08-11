package com.padel.rankpadel.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.padel.rankpadel.enums.CategoriaGasto;
import com.padel.rankpadel.enums.MedioPago;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GastoRequest {

    @NotNull(message = "Indicá la fecha del gasto")
    private LocalDate fecha;

    @NotNull(message = "Elegí una categoría")
    private CategoriaGasto categoria;

    @NotBlank(message = "Describí el gasto")
    @Size(max = 200)
    private String descripcion;

    @NotNull(message = "Indicá el monto")
    @DecimalMin(value = "0.01", message = "El monto tiene que ser mayor a cero")
    private BigDecimal monto;

    @NotNull(message = "Indicá cómo se pagó")
    private MedioPago medio;

    @Size(max = 120)
    private String proveedor;

    @Size(max = 300)
    private String notas;
}
