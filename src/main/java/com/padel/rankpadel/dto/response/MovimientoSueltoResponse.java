package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un movimiento de caja que no es un turno ni una venta.
 *
 * <p>Se llama "suelto" para no confundirlo con {@code MovimientoCajaResponse}, que es otra
 * cosa: ese agrupa los cobros de un turno en una línea de la pantalla de caja.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MovimientoSueltoResponse {

    private Long id;
    private LocalDate jornada;
    private LocalDateTime fecha;
    private String tipo;
    private String concepto;
    private String descripcion;
    private BigDecimal monto;
    private String medio;
    private Long clienteId;
    private String clienteNombre;
    private String registradoPor;
    private String notas;

    private LocalDateTime anuladoEn;
    private String anuladoPor;
    private String motivoAnulacion;
}
