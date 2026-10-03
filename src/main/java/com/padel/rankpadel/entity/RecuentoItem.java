package com.padel.rankpadel.entity;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un renglón de la planilla: un producto, lo que decía el sistema cuando entró a la
 * planilla, y lo que se contó.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "recuento_items")
public class RecuentoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recuento_id")
    private Recuento recuento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    /**
     * Lo que decía el sistema cuando el renglón entró a la planilla, no cuando se aplica.
     *
     * <p>Entre que el club empieza a contar y termina siguen entrando ventas. Aplicar el
     * conteo como valor absoluto borraría esas ventas del inventario; lo que el conteo dice
     * es una diferencia medida en un momento, y eso es lo que se aplica.
     */
    private int stockSistema;

    /** Null = todavía no se contó. Distinto de cero, que es "no quedaba ninguno". */
    private Integer stockContado;

    /** El costo del producto el día del conteo. Valorizar después daría otro número. */
    private BigDecimal costoUnitario;

    /** Positiva si sobró, negativa si faltó. Cero mientras no se cuente. */
    public int diferencia() {
        return stockContado == null ? 0 : stockContado - stockSistema;
    }
}
