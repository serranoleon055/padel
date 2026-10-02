package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una forma de vender el producto: la unidad suelta o el pack. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PresentacionResponse {

    private Long id;
    private String nombre;

    /** Unidades base que saca del stock. Un tubo de 3 pelotas son 3. */
    private int unidades;

    private BigDecimal precioVenta;

    /** Lo que deja cada una, valuada al costo promedio. Null sin costo cargado. */
    private BigDecimal margenUnitario;

    private int orden;
    private boolean activo;
}
