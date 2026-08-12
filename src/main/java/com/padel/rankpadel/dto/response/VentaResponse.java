package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
public class VentaResponse {

    private Long id;
    private LocalDateTime fecha;
    private BigDecimal total;
    private String medio;
    private Long clienteId;
    private String clienteNombre;
    private Long reservaId;
    private String registradoPor;
    private String notas;
    private List<Item> items;

    /** Resumen para la fila del listado: "2 x Pelotas Head, 1 x Gatorade". */
    private String detalle;

    /** Con fecha, la venta está anulada y no suma en ningún total. */
    private LocalDateTime anuladoEn;
    private String anuladoPor;
    private String motivoAnulacion;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Item {
        private Long productoId;
        private String productoNombre;
        private int cantidad;
        private BigDecimal precioUnitario;
        private BigDecimal subtotal;
    }
}
