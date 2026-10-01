package com.padel.rankpadel.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.padel.rankpadel.enums.CategoriaGasto;
import com.padel.rankpadel.enums.MedioPago;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un egreso del club. La fecha es la del gasto (no la de carga): una factura de luz de
 * marzo pagada en abril tiene que pesar en marzo.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "gastos")
public class Gasto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Fecha contable del gasto, la que elige la persona: una factura de luz de marzo
     * pagada en abril pesa en marzo. No es el día de caja — eso es {@link #jornada}.
     */
    private LocalDate fecha;

    /**
     * Jornada del club en la que la plata salió del cajón. Es lo que mira el arqueo, y es
     * otra cosa que {@link #fecha}: un pago en efectivo a las 00:30 es de la noche que
     * arrancó ayer. Se estampa al registrar y no se recalcula nunca, igual que
     * {@code Cobro.jornada} (ver V58).
     */
    private LocalDate jornada;

    @Enumerated(EnumType.STRING)
    private CategoriaGasto categoria;

    private String descripcion;

    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    private MedioPago medio;

    private String proveedor;

    private String registradoPor;

    private String notas;

    private LocalDateTime creadoEn;

    /** Si el gasto fue una compra de mercadería, a qué producto entró. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    /**
     * Si el egreso fue compra de mercadería. Es lo que separa el gasto operativo del
     * inventario en el estado de resultados, y tiene que ser un flag propio: una compra
     * con varios productos en un solo comprobante no puede apuntar a uno
     * ({@link #producto}), y sin el flag se contaría como gasto operativo y otra vez
     * dentro del costo de la mercadería vendida.
     */
    private boolean esMercaderia;

    /**
     * Anulación. Baja lógica y no borrado, mismo criterio que {@code Cobro} y
     * {@code Venta}: los egresos son la mitad del resultado que ve el dueño, y uno que
     * desaparece sin rastro cambia la rentabilidad de un mes cerrado sin nadie a quien
     * preguntarle.
     */
    private LocalDateTime anuladoEn;
    private String anuladoPor;
    private String motivoAnulacion;

    public boolean estaAnulado() {
        return anuladoEn != null;
    }
}
