package com.padel.rankpadel.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Corrección de stock tras contar la vitrina: se manda cuántas unidades hay de verdad y
 * el sistema guarda la diferencia.
 *
 * <p>Existe porque el endpoint recibía un {@code Map<String, Object>} y parseaba el número
 * a mano: un "doce" o un campo vacío salían como error interno en vez de como un mensaje
 * que le sirva a quien está contando.
 */
@Getter
@Setter
@NoArgsConstructor
public class AjusteStockRequest {

    @NotNull(message = "Indicá cuántas unidades hay")
    @Min(value = 0, message = "El stock no puede ser negativo")
    private Integer stockReal;

    @Size(max = 300)
    private String notas;
}
