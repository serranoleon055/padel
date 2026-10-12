package com.padel.rankpadel.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Un premio del torneo: por categoría y puesto, en plata, en productos o las dos cosas.
 * Lo que es en plata cuenta en el balance; marcarlo entregado lo pasa de "a pagar" a
 * "pagado".
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "premios_torneo")
public class PremioTorneo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "torneo_id")
    private Torneo torneo;

    /** Null = premio general del torneo (no de una categoría). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    /** "Campeón", "Subcampeón", "Semifinalistas", "Mejor jugador"... */
    private String puesto;

    /** Lo que no es plata: "Paletas Nox + trofeo". */
    private String descripcion;

    /** La parte en plata. Null = no hay plata. */
    private BigDecimal monto;

    private boolean entregado;
    private LocalDateTime entregadoEn;

    /** El nombre de columna va explícito: la estrategia de nombres no separa una mayúscula final. */
    @Column(name = "entregado_a")
    private String entregadoA;
}
