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
public class ProductoResponse {

    private Long id;
    private String nombre;
    private String categoria;
    private BigDecimal precioVenta;
    private BigDecimal costo;

    /** Ganancia por unidad. Null mientras no se cargue el costo. */
    private BigDecimal margenUnitario;

    private boolean controlaStock;
    private int stock;
    private int stockMinimo;

    /** Quedan menos unidades que el mínimo: hay que reponer. */
    private boolean necesitaReposicion;

    private Long proveedorId;
    private String proveedorNombre;
    private boolean activo;
}
