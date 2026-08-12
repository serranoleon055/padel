package com.padel.rankpadel.dto.request;

import java.util.List;

import com.padel.rankpadel.enums.MedioPago;

import jakarta.validation.Valid;
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

    @Size(max = 300)
    private String notas;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {

        @NotNull(message = "Elegí el producto")
        private Long productoId;

        @Min(value = 1, message = "La cantidad tiene que ser al menos 1")
        private int cantidad;
    }
}
