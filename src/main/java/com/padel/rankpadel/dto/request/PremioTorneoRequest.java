package com.padel.rankpadel.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PremioTorneoRequest {

    /** Null = premio general del torneo. */
    private Long categoriaId;

    @NotBlank(message = "Indicá el puesto (por ejemplo: Campeón)")
    @Size(max = 80)
    private String puesto;

    @Size(max = 255)
    private String descripcion;

    @DecimalMin(value = "0", message = "El monto no puede ser negativo")
    private BigDecimal monto;
}
