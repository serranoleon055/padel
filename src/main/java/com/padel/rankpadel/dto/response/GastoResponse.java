package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GastoResponse {

    private Long id;
    private LocalDate fecha;
    private String categoria;
    private String descripcion;
    private BigDecimal monto;
    private String medio;
    private String proveedor;
    private String registradoPor;
    private String notas;
}
