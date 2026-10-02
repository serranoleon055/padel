package com.padel.rankpadel.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PresentacionRequest {

    @NotBlank(message = "Poné cómo se llama")
    @Size(max = 60)
    private String nombre;

    /**
     * Cuántas unidades base saca del stock. El tope es para atajar el error de tipeo: un
     * pack de mil unidades vaciaría el depósito de una venta.
     */
    @NotNull(message = "Indicá cuántas unidades trae")
    @Min(value = 1, message = "Tiene que traer al menos una unidad")
    @Max(value = 999, message = "Son demasiadas unidades para una presentación")
    private Integer unidades;

    @NotNull(message = "Indicá el precio")
    @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
    private BigDecimal precioVenta;

    private Integer orden;
}
