package com.padel.rankpadel.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.padel.rankpadel.enums.MedioPago;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entrada de mercadería, corrección de stock o merma. Lo que cambia entre las tres es el
 * motivo, así que van por el mismo camino.
 */
@Getter
@Setter
@NoArgsConstructor
public class MovimientoStockRequest {

    /** Unidades que entran (compra) o que se pierden (merma). Siempre en positivo. */
    @Min(value = 1, message = "La cantidad tiene que ser al menos 1")
    private int cantidad;

    /** Solo en una compra: lo que salió cada unidad. Actualiza el costo del producto. */
    @DecimalMin(value = "0.00", message = "El costo no puede ser negativo")
    private BigDecimal costoUnitario;

    /**
     * Si viene, además del ingreso de stock se registra el egreso: la compra de
     * mercadería es plata que salió del club y tiene que pesar en la rentabilidad.
     */
    private MedioPago medioPago;

    /** Fecha del gasto, que puede no ser la de hoy. */
    private LocalDate fecha;

    @Size(max = 300)
    private String notas;
}
