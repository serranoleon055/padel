package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.util.List;

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

    /** Lo que salió la última compra. Es el número que el club tiene en la cabeza. */
    private BigDecimal costo;

    /**
     * Lo que sale en promedio cada unidad de las que hay. Es con el que se valúa el
     * depósito y se mide el margen: con el último costo, una compra chica a precio raro
     * movía de golpe el margen de todo lo que ya estaba en la heladera.
     */
    private BigDecimal costoPromedio;

    /** Ganancia por unidad, al costo promedio. Null mientras no se cargue el costo. */
    private BigDecimal margenUnitario;

    /**
     * Las formas de venderlo: la unidad suelta y los packs. Vacío = se vende solo de a
     * una, por su precio, que es como funciona todo producto sin presentaciones.
     */
    private List<PresentacionResponse> presentaciones;

    private boolean controlaStock;
    private int stock;
    private int stockMinimo;

    /** Quedan menos unidades que el mínimo: hay que reponer. */
    private boolean necesitaReposicion;

    private Long proveedorId;
    private String proveedorNombre;
    private boolean activo;
}
