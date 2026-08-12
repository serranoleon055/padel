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

    /** Lo que le cuesta al club. Sin esto no se puede calcular el margen. */
    private BigDecimal costo;

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

    /** Ganancia por unidad, o null si todavía no se cargó el costo. */
    public BigDecimal margenUnitario() {
        if (costo == null || precioVenta == null) {
            return null;
        }
        return precioVenta.subtract(costo);
    }
}
