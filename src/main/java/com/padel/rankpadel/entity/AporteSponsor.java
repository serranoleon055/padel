package com.padel.rankpadel.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.padel.rankpadel.enums.MedioPago;

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
 * Lo que pone un sponsor para el torneo: plata, productos (canje) o las dos cosas. La
 * plata comprometida cuenta en el balance proyectado; cuando entra, en el real.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "aportes_sponsor")
public class AporteSponsor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "torneo_id")
    private Torneo torneo;

    /** El sponsor cargado en el sistema, si lo está. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sponsor_id")
    private Sponsor sponsor;

    /** El nombre siempre, aunque no esté cargado como sponsor (un canje de una vez). */
    private String nombre;

    /** La parte en plata. Null = es solo canje. */
    private BigDecimal monto;

    /** El canje: "30 tubos de pelotas". */
    private String descripcion;

    private boolean cobrado;
    private LocalDate cobradoEn;

    @Enumerated(EnumType.STRING)
    private MedioPago medio;
}
