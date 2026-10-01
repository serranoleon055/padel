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
 * Una fila del historial de arqueos. Son los totales CONGELADOS en el momento de firmar,
 * no los de hoy: si después se corrigió un turno de ese día, el arqueo sigue diciendo lo
 * que decía cuando alguien contó la plata, que es contra lo que se contó.
 *
 * <p>Los arqueos reabiertos también salen en la lista, con quién los reabrió y por qué.
 * Es la mitad del valor de tener el historial: ver si siempre falta plata el mismo día de
 * la semana, o si siempre reabre el mismo.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ArqueoHistoricoResponse {

    private Long id;
    private LocalDate fecha;

    private BigDecimal efectivoEsperado;
    private BigDecimal efectivoContado;
    /** Contado menos esperado. Negativo = faltó plata en el cajón. */
    private BigDecimal diferencia;

    private BigDecimal totalMostrador;
    private BigDecimal seniasOnline;
    private BigDecimal egresos;

    private String cerradoPor;
    private LocalDateTime cerradoEn;
    private String notas;

    /** Null si el arqueo sigue vigente. */
    private LocalDateTime reabiertoEn;
    private String reabiertoPor;
    private String motivoReapertura;
}
