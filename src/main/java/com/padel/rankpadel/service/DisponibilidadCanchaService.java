package com.padel.rankpadel.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.response.SlotDisponibilidad;
import com.padel.rankpadel.entity.BloqueoCancha;
import com.padel.rankpadel.entity.HorarioCancha;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.repository.BloqueoCanchaRepository;
import com.padel.rankpadel.repository.HorarioCanchaRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.ReservaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisponibilidadCanchaService {

    private static final int DURACION_PARTIDO_MIN = 90;
    private static final int DURACION_SLOT_DEFECTO = 60;
    private static final int MAXIMO_SLOTS = 200;
    private static final Set<EstadoReserva> ESTADOS_ACTIVOS =
            Set.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA);

    private final HorarioCanchaRepository horarioCanchaRepository;
    private final ReservaRepository reservaRepository;
    private final BloqueoCanchaRepository bloqueoCanchaRepository;
    private final PartidoRepository partidoRepository;

    public List<SlotDisponibilidad> slots(Long canchaId, LocalDate fecha) {
        HorarioCancha horario = horarioActivo(canchaId);
        if (horario == null || !fechaHabilitada(horario, fecha)) {
            return List.of();
        }

        int duracion = horario.getDuracionSlotMin() > 0 ? horario.getDuracionSlotMin() : DURACION_SLOT_DEFECTO;
        LocalTime apertura = horario.getHoraApertura();
        LocalDateTime inicioSesion = fecha.atTime(apertura);
        LocalDateTime finSesion = fecha.atTime(horario.getHoraCierre());
        if (!finSesion.isAfter(inicioSesion)) {
            finSesion = finSesion.plusDays(1);
        }

        List<Intervalo> ocupaciones = ocupaciones(canchaId, fecha, apertura, inicioSesion, finSesion, null);
        List<SlotDisponibilidad> slots = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now();

        LocalDateTime inicio = inicioSesion;
        int contador = 0;
        while (inicio.isBefore(finSesion) && contador++ < MAXIMO_SLOTS) {
            LocalDateTime fin = inicio.plusMinutes(duracion);
            if (inicio.isAfter(ahora)) {
                slots.add(SlotDisponibilidad.builder()
                        .horaInicio(inicio.toLocalTime())
                        .horaFin(fin.toLocalTime())
                        .disponible(libre(ocupaciones, inicio, fin))
                        .build());
            }
            inicio = fin;
        }
        return slots;
    }

    public boolean rangoLibre(Long canchaId, LocalDate fecha, LocalTime inicio, LocalTime fin) {
        LocalTime apertura = aperturaActiva(canchaId);
        LocalDateTime inicioReal = aDateTime(fecha, inicio, apertura);
        LocalDateTime finReal = aDateTime(fecha, fin, apertura);
        if (!finReal.isAfter(inicioReal)) {
            finReal = finReal.plusDays(1);
        }
        LocalDateTime ventanaInicio = fecha.atTime(apertura);
        LocalDateTime ventanaFin = ventanaInicio.plusDays(1);
        return libre(ocupaciones(canchaId, fecha, apertura, ventanaInicio, ventanaFin, null), inicioReal, finReal);
    }

    public boolean canchaLibreParaPartido(Long canchaId, LocalDateTime inicio, Long partidoIdExcluido) {
        LocalDate fecha = inicio.toLocalDate();
        LocalDateTime ventanaInicio = fecha.atStartOfDay();
        LocalDateTime ventanaFin = fecha.plusDays(1).atStartOfDay();
        return libre(ocupaciones(canchaId, fecha, LocalTime.MIN, ventanaInicio, ventanaFin, partidoIdExcluido),
                inicio, inicio.plusMinutes(DURACION_PARTIDO_MIN));
    }

    public LocalDateTime inicioReal(Long canchaId, LocalDate fecha, LocalTime hora) {
        return aDateTime(fecha, hora, aperturaActiva(canchaId));
    }

    public int duracionSlot(Long canchaId) {
        HorarioCancha horario = horarioActivo(canchaId);
        if (horario == null || horario.getDuracionSlotMin() <= 0) {
            return DURACION_SLOT_DEFECTO;
        }
        return horario.getDuracionSlotMin();
    }

    private LocalTime aperturaActiva(Long canchaId) {
        HorarioCancha horario = horarioActivo(canchaId);
        return horario != null ? horario.getHoraApertura() : LocalTime.MIN;
    }

    private LocalDateTime aDateTime(LocalDate fecha, LocalTime hora, LocalTime apertura) {
        return hora.isBefore(apertura) ? fecha.plusDays(1).atTime(hora) : fecha.atTime(hora);
    }

    private HorarioCancha horarioActivo(Long canchaId) {
        List<HorarioCancha> horarios = horarioCanchaRepository.findByCanchaIdAndActivoTrue(canchaId);
        return horarios.isEmpty() ? null : horarios.get(0);
    }

    private boolean fechaHabilitada(HorarioCancha horario, LocalDate fecha) {
        LocalDate hoy = LocalDate.now();
        if (fecha.isBefore(hoy)) {
            return false;
        }
        if (horario.getAnticipacionDias() > 0 && fecha.isAfter(hoy.plusDays(horario.getAnticipacionDias()))) {
            return false;
        }
        return diaActivo(horario.getDiasActivos(), fecha);
    }

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

    private List<Intervalo> ocupaciones(Long canchaId, LocalDate fecha, LocalTime apertura,
            LocalDateTime ventanaInicio, LocalDateTime ventanaFin, Long partidoIdExcluido) {
        List<Intervalo> intervalos = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now();

        for (Reserva reserva : reservaRepository.findByCanchaIdAndFecha(canchaId, fecha)) {
            if (!ESTADOS_ACTIVOS.contains(reserva.getEstado())) {
                continue;
            }
            if (reserva.getEstado() == EstadoReserva.PENDIENTE
                    && reserva.getExpiraEn() != null
                    && reserva.getExpiraEn().isBefore(ahora)) {
                continue;
            }
            LocalDateTime inicio = aDateTime(fecha, reserva.getHoraInicio(), apertura);
            LocalDateTime fin = aDateTime(fecha, reserva.getHoraFin(), apertura);
            if (!fin.isAfter(inicio)) {
                fin = fin.plusDays(1);
            }
            intervalos.add(new Intervalo(inicio, fin));
        }

        for (BloqueoCancha bloqueo : bloqueoCanchaRepository.findByCanchaId(canchaId)) {
            intervalos.add(new Intervalo(bloqueo.getInicio(), bloqueo.getFin()));
        }

        for (Partido partido : partidoRepository.findByCanchaIdAndFechaHoraProgramadaBetween(
                canchaId, ventanaInicio, ventanaFin)) {
            if (partido.getFechaHoraProgramada() == null) {
                continue;
            }
            if (partidoIdExcluido != null && partidoIdExcluido.equals(partido.getId())) {
                continue;
            }
            intervalos.add(new Intervalo(partido.getFechaHoraProgramada(),
                    partido.getFechaHoraProgramada().plusMinutes(DURACION_PARTIDO_MIN)));
        }

        return intervalos;
    }

    private boolean libre(List<Intervalo> ocupaciones, LocalDateTime inicio, LocalDateTime fin) {
        for (Intervalo ocupado : ocupaciones) {
            if (inicio.isBefore(ocupado.fin()) && ocupado.inicio().isBefore(fin)) {
                return false;
            }
        }
        return true;
    }

    private record Intervalo(LocalDateTime inicio, LocalDateTime fin) {
    }
}
