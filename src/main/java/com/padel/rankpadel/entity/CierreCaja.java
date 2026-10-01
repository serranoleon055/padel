package com.padel.rankpadel.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
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
 * El arqueo firmado de un día: alguien contó el cajón y dejó asentado cuánto había.
 *
 * <p>Los totales se guardan congelados. Si mañana se corrige un turno viejo, el arqueo de
 * ayer no puede moverse: era lo que se sabía en el momento de cerrar, y contra eso se
 * contó la plata. Una vez cerrado el día, los cobros, ventas y gastos de esa fecha no se
 * tocan más.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "cierres_caja")
public class CierreCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate fecha;

    /** Lo que el sistema decía que tenía que haber en el cajón. */
    private BigDecimal efectivoEsperado;

    /** Lo que había de verdad. */
    private BigDecimal efectivoContado;

    /** Contado menos esperado. Negativo = falta plata. */
    private BigDecimal diferencia;

    private BigDecimal totalMostrador;
    private BigDecimal seniasOnline;
    private BigDecimal egresos;

    private String cerradoPor;
    private LocalDateTime cerradoEn;
    private String notas;

    /**
     * Reapertura. Es baja lógica y no borrado: el arqueo es el único lugar donde consta
     * que a alguien le faltó plata, así que reabrir no puede hacerlo desaparecer. La fila
     * queda y la jornada vuelve a quedar abierta.
     *
     * <p>La columna generada {@code fecha_vigente} de V59 es la que deja que convivan
     * varios arqueos anulados de la misma jornada y como máximo uno en pie. No se mapea
     * acá a propósito: la calcula la base.
     */
    private LocalDateTime anuladoEn;
    private String anuladoPor;
    private String motivoReapertura;

    public boolean estaVigente() {
        return anuladoEn == null;
    }
}
