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
 * Un renglón de la venta. El precio y el costo se congelan acá: si el club actualiza la
 * lista de precios, lo que ya vendió no puede cambiar de valor ni de margen.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "venta_items")
public class VentaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id")
    private Venta venta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    /**
     * Cómo se vendió: suelta o por tubo. Null es la unidad base, que es cómo se vendió
     * todo hasta V62.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentacion_id")
    private PresentacionProducto presentacion;

    /** Cuántas se vendieron de esta presentación: 2 tubos son cantidad 2. */
    private int cantidad;

    /**
     * Unidades base que sale cada una. CONGELADO igual que el precio y el costo: cambiar
     * después cuántas pelotas trae un tubo no puede reescribir lo que salió del stock
     * aquel día.
     */
    @Builder.Default
    private int factor = 1;

    /** Precio de la presentación al momento de vender. Congelado. */
    private BigDecimal precioUnitario;

    /**
     * Costo de la presentación al momento de vender, ya multiplicado por el factor: el
     * costo de un tubo es el de tres pelotas. Sin eso el margen de un tubo se calcularía
     * contra el costo de una sola y el kiosco parecería una mina de oro.
     */
    private BigDecimal costoUnitario;

    public BigDecimal subtotal() {
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    /** Lo que esta línea saca del stock, en unidades base. */
    public int unidadesBase() {
        return cantidad * Math.max(1, factor);
    }
}
