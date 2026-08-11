package com.padel.rankpadel.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.padel.rankpadel.enums.CategoriaGasto;
import com.padel.rankpadel.enums.MedioPago;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    private LocalDate fecha;

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
}
