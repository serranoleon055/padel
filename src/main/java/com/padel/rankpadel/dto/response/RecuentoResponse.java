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

/** La planilla de conteo con sus renglones y la diferencia valorizada. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RecuentoResponse {

    private Long id;
    private LocalDate fecha;
    /** BORRADOR o APLICADO. */
    private String estado;
    private String notas;

    private LocalDateTime creadoEn;
    private String creadoPor;
    private LocalDateTime aplicadoEn;
    private String aplicadoPor;

    /**
     * Lo que valía lo que faltó, en positivo. Mientras está en BORRADOR es lo que se
     * perdería al aplicar; aplicado, queda congelado.
     */
    private BigDecimal faltanteValorizado;
    private BigDecimal sobranteValorizado;

    /** Cuántos renglones ya se contaron, de cuántos hay. */
    private int contados;
    private int total;

    private List<Item> items;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Item {
        private Long id;
        private Long productoId;
        private String productoNombre;
        private String categoria;
        /** Lo que decía el sistema cuando el renglón entró a la planilla. */
        private int stockSistema;
        /** Null = todavía no se contó. */
        private Integer stockContado;
        /** Positiva si sobró, negativa si faltó. */
        private int diferencia;
        private BigDecimal costoUnitario;
        /** `diferencia × costoUnitario`. Null si el producto no tiene costo cargado. */
        private BigDecimal valorizada;
    }
}
