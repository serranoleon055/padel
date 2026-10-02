package com.padel.rankpadel.dto.request;

import java.math.BigDecimal;

import com.padel.rankpadel.enums.ConceptoMovimientoCaja;
import com.padel.rankpadel.enums.MedioPago;
import com.padel.rankpadel.enums.TipoMovimientoCaja;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un movimiento de caja que no es un turno ni una venta: el fondo de apertura, un depósito
 * al banco, un retiro del dueño, el alquiler del salón.
 *
 * <p>El tipo viene acá porque hay conceptos que van en los dos sentidos (un préstamo se da
 * y se devuelve, un ajuste puede ser de más o de menos). Pero los que tienen un único
 * sentido posible lo imponen: cargar un "retiro del dueño" como ingreso sería un
 * movimiento que no existe, y el servicio lo rechaza.
 */
@Getter
@Setter
@NoArgsConstructor
public class MovimientoCajaRequest {

    @NotNull(message = "Elegí de qué se trata")
    private ConceptoMovimientoCaja concepto;

    /** Opcional en los conceptos que van en un solo sentido: ahí lo pone el servidor. */
    private TipoMovimientoCaja tipo;

    @NotBlank(message = "Escribí de qué es el movimiento")
    @Size(max = 300)
    private String descripcion;

    /** Siempre en positivo: si es ingreso o egreso lo define el concepto. */
    @NotNull(message = "Indicá el monto")
    @DecimalMin(value = "0.01", message = "El monto tiene que ser mayor a cero")
    private BigDecimal monto;

    @NotNull(message = "Indicá por qué medio")
    private MedioPago medio;

    private Long clienteId;

    @Size(max = 300)
    private String notas;
}
