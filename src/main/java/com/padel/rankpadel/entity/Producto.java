package com.padel.rankpadel.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.padel.rankpadel.enums.CategoriaProducto;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Algo que el club vende en el mostrador: un tubo de pelotas, una gaseosa, el alquiler
 * de una paleta.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "productos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Bloqueo optimista sobre el stock. Dos ventas simultáneas de la última unidad leían
     * las dos stock 1, las dos pasaban el control y las dos escribían 0: se vendían dos
     * unidades habiendo una. Con la versión, la segunda falla y el mostrador reintenta.
     */
    @Version
    private Long version;

    private String nombre;

    @Enumerated(EnumType.STRING)
    private CategoriaProducto categoria;

    private BigDecimal precioVenta;

    /**
     * Lo que salió la ÚLTIMA compra. Es el número que el club tiene en la cabeza cuando
     * mira el precio, así que se muestra; pero para valuar el depósito y medir el margen
     * se usa {@link #costoPromedio}.
     */
    private BigDecimal costo;

    /**
     * Costo promedio ponderado: lo que salió en promedio cada unidad de las que hay.
     *
     * <p>Con el último costo, una compra chica a precio raro movía de golpe el capital en
     * stock y el margen de todo lo que ya estaba en la heladera. Se recalcula en cada
     * compra ponderando por las unidades que había y las que entran.
     */
    private BigDecimal costoPromedio;

    /**
     * Un alquiler de paleta o un café no tienen unidades que se acaben. Con el control
     * apagado se vende siempre y no se descuenta nada.
     */
    @Builder.Default
    private boolean controlaStock = true;

    @Builder.Default
    private int stock = 0;

    /** A partir de acá el sistema avisa que hay que reponer. */
    @Builder.Default
    private int stockMinimo = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    @Builder.Default
    private boolean activo = true;

    private LocalDateTime creadoEn;

    /** Hay que reponer: quedan menos unidades que el mínimo que fijó el club. */
    public boolean necesitaReposicion() {
        return controlaStock && stockMinimo > 0 && stock <= stockMinimo;
    }

    /**
     * El costo con el que se valúa el stock y se mide el margen: el promedio ponderado, y
     * el último costo solo mientras no haya promedio (productos que nunca se compraron
     * por el sistema).
     */
    public BigDecimal costoDeValuacion() {
        return costoPromedio != null ? costoPromedio : costo;
    }

    /** Ganancia por unidad, o null si todavía no se cargó el costo. */
    public BigDecimal margenUnitario() {
        BigDecimal base = costoDeValuacion();
        if (base == null || precioVenta == null) {
            return null;
        }
        return precioVenta.subtract(base);
    }
}
