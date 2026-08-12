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
public class MovimientoStockResponse {

    private Long id;
    private Long productoId;
    private String productoNombre;

    /** Negativo cuando salió mercadería, positivo cuando entró. */
    private int cantidad;

    private String motivo;
    private LocalDateTime fecha;
    private BigDecimal costoUnitario;
    private String registradoPor;
    private String notas;
}
