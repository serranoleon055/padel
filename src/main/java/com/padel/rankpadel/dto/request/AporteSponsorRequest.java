package com.padel.rankpadel.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.padel.rankpadel.enums.MedioPago;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AporteSponsorRequest {

    /** El sponsor cargado en el sistema. Si no viene, va {@link #nombre}. */
    private Long sponsorId;

    @Size(max = 120)
    private String nombre;

    @DecimalMin(value = "0", message = "El monto no puede ser negativo")
    private BigDecimal monto;

    @Size(max = 255)
    private String descripcion;

    private boolean cobrado;
    private LocalDate cobradoEn;
    private MedioPago medio;
}
