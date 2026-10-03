package com.padel.rankpadel.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.padel.rankpadel.enums.MedioPago;
import com.padel.rankpadel.enums.TipoComprobanteCompra;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una compra tal como vino el papel: un comprobante con varios productos. */
@Getter
@Setter
@NoArgsConstructor
public class CompraRequest {

    @NotNull(message = "Elegí el proveedor")
    private Long proveedorId;

    @NotNull(message = "Indicá qué comprobante es")
    private TipoComprobanteCompra tipoComprobante;

    @NotBlank(message = "Poné el número del comprobante")
    @Size(max = 40)
    private String numero;

    @NotNull(message = "Indicá la fecha del comprobante")
    private LocalDate fecha;

    /**
     * Cómo se pagó. Null = queda a cuenta corriente del proveedor: la mercadería entró
     * pero la plata todavía no salió.
     */
    private MedioPago medio;

    @NotEmpty(message = "Agregá al menos un producto")
    @Valid
    private List<Item> items;

    @Size(max = 300)
    private String notas;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {

        @NotNull(message = "Elegí el producto")
        private Long productoId;

        /** Unidades base que entran. Un tubo de 3 se carga como 3 pelotas. */
        @Min(value = 1, message = "La cantidad tiene que ser al menos 1")
        private int cantidad;

        @NotNull(message = "Indicá cuánto costó cada unidad")
        @DecimalMin(value = "0.00", message = "El costo no puede ser negativo")
        private BigDecimal costoUnitario;
    }
}
