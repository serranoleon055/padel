package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;

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
public class PagoResponse {

    private Long id;
    private String concepto;
    private String estado;
    private BigDecimal montoTotal;
    private BigDecimal montoSenia;
}
