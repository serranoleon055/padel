package com.padel.rankpadel.dto.request;

import java.math.BigDecimal;

import com.padel.rankpadel.enums.CategoriaProducto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductoRequest {

    @NotBlank(message = "El nombre del producto es obligatorio")
    @Size(max = 120)
    private String nombre;

    @NotNull(message = "Elegí el rubro")
    private CategoriaProducto categoria;

    @NotNull(message = "Indicá el precio de venta")
    @DecimalMin(value = "0.01", message = "El precio tiene que ser mayor a cero")
    private BigDecimal precioVenta;

    /** Null si el club todavía no sabe cuánto le cuesta. */
    @DecimalMin(value = "0.00", message = "El costo no puede ser negativo")
    private BigDecimal costo;

    /** Falso para lo que no se agota, como el alquiler de una paleta. */
    private boolean controlaStock = true;

    /** Solo al crear: las unidades que ya hay en la vitrina. */
    @Min(value = 0, message = "El stock no puede ser negativo")
    private Integer stockInicial;

    @Min(value = 0, message = "El mínimo no puede ser negativo")
    private Integer stockMinimo;

    private Long proveedorId;

    private Boolean activo;
}
