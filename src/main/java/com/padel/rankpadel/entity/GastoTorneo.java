package com.padel.rankpadel.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.padel.rankpadel.enums.CategoriaGastoTorneo;
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
 * Un gasto del torneo que no es el club ni un premio: pelotas, árbitro, fotógrafo,
 * trofeos. Es plata que ya salió. No se borra: se anula, como el resto de los pagos.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "gastos_torneo")
public class GastoTorneo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "torneo_id")
    private Torneo torneo;

    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    private CategoriaGastoTorneo categoria;

    private String descripcion;
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    private MedioPago medio;

    private String registradoPor;
    private LocalDateTime creadoEn;

    private LocalDateTime anuladoEn;
    private String anuladoPor;
    private String motivoAnulacion;

    public boolean estaAnulado() {
        return anuladoEn != null;
    }
}
