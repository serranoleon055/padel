package com.padel.rankpadel.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.padel.rankpadel.enums.MotivoMovimientoStock;

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
 * Cada entrada y salida de mercadería. El stock del producto es la suma de todos sus
 * movimientos: si un día falta algo, acá está por qué.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "movimientos_stock")
public class MovimientoStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    /** Negativo cuando sale mercadería, positivo cuando entra. */
    private int cantidad;

    @Enumerated(EnumType.STRING)
    private MotivoMovimientoStock motivo;

    private LocalDateTime fecha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id")
    private Venta venta;

    /** Cuánto costó la unidad en esta compra. */
    private BigDecimal costoUnitario;

    private String registradoPor;
    private String notas;
}
