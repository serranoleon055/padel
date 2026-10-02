package com.padel.rankpadel.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.HorarioCancha;

/**
 * Cuándo está abierto el club: sus canchas y las bandas de horario de cada una, leídas
 * una sola vez.
 *
 * <p>Existe por un problema concreto de las estadísticas: la apertura del lugar, las horas
 * abiertas del mes y la ocupación por cancha preguntaban cada una por su cuenta, y cada
 * una iba cancha por cancha. Con seis canchas eran dieciocho consultas de horario por
 * request para leer siempre lo mismo. Acá se carga una vez y se responde en memoria.
 *
 * <p>No es un bean: se arma por request con los datos ya traídos, así no hay nada que
 * invalidar cuando el club cambia un horario.
 */
public final class CalendarioClub {

    private final List<Cancha> canchas;
    private final Map<Long, List<HorarioCancha>> horariosPorCancha;

    private CalendarioClub(List<Cancha> canchas, Map<Long, List<HorarioCancha>> horariosPorCancha) {
        this.canchas = canchas;
        this.horariosPorCancha = horariosPorCancha;
    }

    public static CalendarioClub de(List<Cancha> canchas, List<HorarioCancha> horarios) {
        Map<Long, List<HorarioCancha>> porCancha = horarios.stream()
                .filter(horario -> horario.getCancha() != null)
                .collect(Collectors.groupingBy(horario -> horario.getCancha().getId()));
        return new CalendarioClub(canchas, porCancha);
    }

    public List<Cancha> canchas() {
        return canchas;
    }

    /**
     * Hora a la que abre la sucursal: la apertura más temprana de sus canchas. Es el punto
     * donde arranca la jornada para todo lo que se muestre por horario.
     */
    public int horaApertura() {
        return canchas.stream()
                .flatMap(cancha -> horariosDe(cancha.getId()).stream())
                .map(HorarioCancha::getHoraApertura)
                .filter(Objects::nonNull)
                .mapToInt(LocalTime::getHour)
                .min()
                .orElse(0);
    }

    /**
     * Las horas que una cancha estuvo abierta en el período, sumando TODAS sus bandas.
     *
     * <p>Varios horarios activos de la misma cancha son bandas por día de la semana —una
     * para la semana y otra para el fin de semana—, y cada banda aporta solo los días que
     * tiene marcados, así que sumarlas no duplica horas.
     */
    public long horasAbiertas(Long canchaId, LocalDate desde, LocalDate hasta) {
        long total = 0;
        for (HorarioCancha horario : horariosDe(canchaId)) {
            if (horario.getHoraApertura() == null || horario.getHoraCierre() == null) {
                continue;
            }
            long horasPorDia = Duration.between(horario.getHoraApertura(), horario.getHoraCierre()).toHours();
            if (horasPorDia <= 0) {
                // Cierra después de medianoche: la jornada cruza el día.
                horasPorDia += 24;
            }
            long dias = desde.datesUntil(hasta.plusDays(1))
                    .filter(dia -> diaActivo(horario.getDiasActivos(), dia))
                    .count();
            total += horasPorDia * dias;
        }
        return total;
    }

    /** Horas de cancha que el club tuvo a la venta en el período, sumando todas. */
    public long horasAbiertasDeTodas(LocalDate desde, LocalDate hasta) {
        return canchas.stream()
                .mapToLong(cancha -> horasAbiertas(cancha.getId(), desde, hasta))
                .sum();
    }

    private List<HorarioCancha> horariosDe(Long canchaId) {
        return horariosPorCancha.getOrDefault(canchaId, List.of());
    }

    /**
     * Los días activos son un CSV de números de día de la semana (1 = lunes). Vacío
     * significa todos los días.
     */
    private boolean diaActivo(String diasActivos, LocalDate fecha) {
        if (diasActivos == null || diasActivos.isBlank()) {
            return true;
        }
        String dia = String.valueOf(fecha.getDayOfWeek().getValue());
        for (String token : diasActivos.split(",")) {
            if (token.trim().equals(dia)) {
                return true;
            }
        }
        return false;
    }
}
