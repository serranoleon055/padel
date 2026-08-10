package com.padel.rankpadel.dto.request;

import java.math.BigDecimal;

import com.padel.rankpadel.enums.MedioCobro;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CobroRequest {

    @NotNull(message = "Indicá el monto cobrado")
    @DecimalMin(value = "0.01", message = "El monto tiene que ser mayor a cero")
    private BigDecimal monto;

    @NotNull(message = "Indicá cómo se cobró")
    private MedioCobro medio;

    @Size(max = 300)
    private String notas;
}
