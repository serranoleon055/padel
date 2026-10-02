package com.padel.rankpadel.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
 * Una forma de vender un producto: la pelota suelta o el tubo de tres.
 *
 * <p>El stock del producto se lleva siempre en la unidad base —la pelota— y cada
 * presentación dice cuántas unidades base saca y a qué precio se vende. Vender un tubo
 * descuenta tres.
 *
 * <p>El precio es propio y no se deriva del de la unidad: el pack casi siempre sale más
 * barato que la suma, y esa es justamente la razón de que el club lo venda así.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "presentaciones_producto")
public class PresentacionProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    /** Cómo la ve el mostrador: "Suelta", "Tubo de 3". */
    private String nombre;

    /** Unidades base que saca del stock. */
    private int unidades;

    private BigDecimal precioVenta;

    /** Para que el mostrador vea primero la que más vende. */
    @Builder.Default
    private int orden = 0;

    @Builder.Default
    private boolean activo = true;

    private LocalDateTime creadoEn;
}
