package com.padel.rankpadel.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un horario en que la pareja NO puede jugar. Se guarda lo que no pueden y no lo que
 * pueden: la mayoría puede siempre salvo una o dos cosas ("el sábado antes de las 14
 * trabajo"), y así el que no carga nada queda disponible.
 *
 * <p>Es un día puntual del torneo ({@link #fecha}) o, para una liga de meses, un día de
 * la semana que se repite ({@link #diaSemana}, 1 = lunes ... 7 = domingo). Uno de los dos.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Embeddable
public class RestriccionHoraria {

    private LocalDate fecha;

    @Column(name = "dia_semana")
    private Integer diaSemana;

    @Column(name = "hora_desde")
    private LocalTime horaDesde;

    @Column(name = "hora_hasta")
    private LocalTime horaHasta;

    /** Si un partido que va de {@code inicio} a {@code fin} cae, aunque sea en parte, acá. */
    public boolean pisa(LocalDateTime inicio, LocalDateTime fin) {
        boolean mismoDia = fecha != null
                ? fecha.equals(inicio.toLocalDate())
                : diaSemana != null && diaSemana == inicio.getDayOfWeek().getValue();
        if (!mismoDia) {
            return false;
        }
        return inicio.toLocalTime().isBefore(horaHasta) && fin.toLocalTime().isAfter(horaDesde);
    }
}
