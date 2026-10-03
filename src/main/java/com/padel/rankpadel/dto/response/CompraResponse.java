package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una compra a proveedor, con sus renglones. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CompraResponse {

    private Long id;
    private Long proveedorId;
    private String proveedorNombre;
    private String tipoComprobante;
    private String numero;
    /** La del comprobante: a qué mes pesa la compra. */
    private LocalDate fecha;
    /** Jornada de caja en la que se cargó. */
    private LocalDate jornada;
    private BigDecimal total;
    /** Null = quedó a cuenta corriente del proveedor. */
    private String medio;
    private boolean aCredito;
    private String registradoPor;
    private String notas;

    /** Los renglones. Son los movimientos de stock que trajo esta compra. */
    private List<Item> items;

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
        private BigDecimal costoUnitario;
        private BigDecimal subtotal;
    }
}
