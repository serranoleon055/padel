package com.padel.rankpadel.dto.request;

import java.math.BigDecimal;
import java.util.List;

import com.padel.rankpadel.enums.MedioPago;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VentaRequest {

    @NotEmpty(message = "Agregá al menos un producto")
    @Valid
    private List<Item> items;

    /**
     * Cómo pagó. Null = va a la cuenta del turno y se cobra al final junto con la
     * cancha; en ese caso {@code reservaId} es obligatorio.
     */
    private MedioPago medio;

    /** Opcional: para que la compra quede en la ficha del cliente. */
    private Long clienteId;

    /** Opcional: sumar la consumición a un turno. */
    private Long reservaId;

    /**
     * Lo que se bonifica sobre el total, en plata.
     *
     * <p>Es un importe y no un porcentaje porque el mostrador redondea ("quedate con cinco
     * mil"), no calcula. La pantalla puede ofrecer el porcentaje; lo que viaja y se guarda
     * es la plata, así ningún redondeo hace que el total no dé.
     *
     * <p>Se reparte entre los renglones al cargar la venta.
     */
    @DecimalMin(value = "0.00", message = "El descuento no puede ser negativo")
    private BigDecimal descuento;

    /** Obligatorio si hay descuento: es lo que después explica un margen flojo. */
    @Size(max = 200)
    private String motivoDescuento;

    @Size(max = 300)
    private String notas;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {

        @NotNull(message = "Elegí el producto")
        private Long productoId;

        /**
         * Cómo se vende: suelta o por tubo. Null es la unidad base, que es cómo se vendió
         * todo hasta V62 y sigue siendo lo normal en los productos sin presentaciones.
         */
        private Long presentacionId;

        /** Cuántas de ESA presentación: 2 tubos son 2, no 6. */
        @Min(value = 1, message = "La cantidad tiene que ser al menos 1")
        private int cantidad;
    }
}
