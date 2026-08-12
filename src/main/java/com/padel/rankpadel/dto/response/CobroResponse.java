package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
public class CobroResponse {

    private Long id;
    private Long reservaId;
    private BigDecimal monto;
    private String medio;
    private LocalDateTime cobradoEn;
    private String registradoPor;
    private String notas;
    private String clienteNombre;
    private String canchaNombre;

    /** Con fecha, el cobro está anulado y no suma en ningún total. */
    private LocalDateTime anuladoEn;
    private String anuladoPor;
    private String motivoAnulacion;
}
