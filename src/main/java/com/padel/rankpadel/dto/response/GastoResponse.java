package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
public class GastoResponse {

    private Long id;

    /** Fecha contable: a qué mes pesa el gasto. La elige la persona. */
    private LocalDate fecha;

    /** Jornada de caja en la que salió la plata. La estampa el sistema. */
    private LocalDate jornada;

    private String categoria;
    private String descripcion;
    private BigDecimal monto;
    private String medio;
    private String proveedor;
    private String registradoPor;
    private String notas;

    /** Si el egreso fue compra de mercadería y no gasto operativo. */
    private boolean esMercaderia;

    private LocalDateTime anuladoEn;
    private String anuladoPor;
    private String motivoAnulacion;
}
