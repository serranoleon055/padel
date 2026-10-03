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

    /**
     * Unidades que quedaban después de este movimiento.
     *
     * <p>Es lo que convierte la lista en un kardex: sin el saldo, una fila de "-3" no dice
     * si quedaron veinte o si se vendió lo último. Viaja en cero en el historial de compras
     * de todo el club, donde no significa nada: ahí cada fila es de otro producto.
     */
    private int saldo;

    private String motivo;
    private LocalDateTime fecha;
    private BigDecimal costoUnitario;
    private String registradoPor;
    private String notas;
}
