package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.response.EstadisticasResponse;
import com.padel.rankpadel.dto.response.EstadisticasResponse.CanchaUso;
import com.padel.rankpadel.dto.response.EstadisticasResponse.CategoriaDemanda;
import com.padel.rankpadel.dto.response.EstadisticasResponse.EmbudoTorneo;
import com.padel.rankpadel.dto.response.EstadisticasResponse.IngresoMes;
import com.padel.rankpadel.dto.response.EstadisticasResponse.OcupacionFranja;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.entity.SolicitudInscripcion;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.enums.EstadoSolicitud;
import com.padel.rankpadel.enums.EstadoTorneo;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.ReservaRepository;
import com.padel.rankpadel.repository.SolicitudInscripcionRepository;
import com.padel.rankpadel.repository.TorneoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EstadisticaService {

    private final ReservaRepository reservaRepository;
    private final TorneoRepository torneoRepository;
    private final ParejaRepository parejaRepository;
    private final SolicitudInscripcionRepository solicitudInscripcionRepository;

    private static final List<EstadoReserva> OCUPAN = List.of(EstadoReserva.CONFIRMADA, EstadoReserva.FINALIZADA);
    private static final List<EstadoReserva> LIBERADAS = List.of(
            EstadoReserva.CANCELADA, EstadoReserva.RECHAZADA, EstadoReserva.EXPIRADA);
    private static final List<EstadoTorneo> TORNEOS_ABIERTOS = List.of(
            EstadoTorneo.INSCRIPCION, EstadoTorneo.SORTEADO, EstadoTorneo.EN_CURSO);

    @Transactional(readOnly = true)
    public EstadisticasResponse obtener(Long lugarId) {
        LocalDate hoy = LocalDate.now();
        LocalDate desde = YearMonth.from(hoy).minusMonths(5).atDay(1);

        List<Reserva> reservas = reservaRepository
                .findByFechaBetweenAndEstadoIn(desde, hoy, Arrays.asList(EstadoReserva.values())).stream()
                .filter(reserva -> deLugar(reserva, lugarId))
                .toList();

        List<Reserva> ocupadas = reservas.stream()
                .filter(reserva -> OCUPAN.contains(reserva.getEstado()) && reserva.getHoraInicio() != null && reserva.getFecha() != null)
                .toList();

        List<OcupacionFranja> heatmap = calcularHeatmap(ocupadas);
        List<CanchaUso> canchasMasUsadas = calcularCanchasMasUsadas(ocupadas);

        List<SolicitudInscripcion> solicitudes = solicitudInscripcionRepository.findAll().stream()
                .filter(solicitud -> solicitud.getTorneo() != null && deLugarTorneo(solicitud.getTorneo(), lugarId))
                .toList();

        List<IngresoMes> ingresosPorMes = calcularIngresosPorMes(hoy, ocupadas, solicitudes);

        long reservasTotales = reservas.size();
        long reservasCanceladas = reservas.stream()
                .filter(reserva -> LIBERADAS.contains(reserva.getEstado()))
                .count();
        double tasaCancelacion = reservasTotales > 0 ? (double) reservasCanceladas / reservasTotales : 0d;

        List<EmbudoTorneo> embudoTorneos = calcularEmbudo(lugarId, solicitudes);
        List<CategoriaDemanda> categoriasDemandadas = calcularCategoriasDemandadas(solicitudes);

        return EstadisticasResponse.builder()
                .heatmap(heatmap)
                .canchasMasUsadas(canchasMasUsadas)
                .ingresosPorMes(ingresosPorMes)
                .reservasTotales(reservasTotales)
                .reservasCanceladas(reservasCanceladas)
                .tasaCancelacion(tasaCancelacion)
                .embudoTorneos(embudoTorneos)
                .categoriasDemandadas(categoriasDemandadas)
                .build();
    }

    private List<OcupacionFranja> calcularHeatmap(List<Reserva> ocupadas) {
        Map<String, Long> conteo = new HashMap<>();
        for (Reserva reserva : ocupadas) {
            int dia = reserva.getFecha().getDayOfWeek().getValue();
            int hora = reserva.getHoraInicio().getHour();
            conteo.merge(dia + "|" + hora, 1L, Long::sum);
        }
        return conteo.entrySet().stream()
                .map(entrada -> {
                    String[] partes = entrada.getKey().split("\\|");
                    return OcupacionFranja.builder()
                            .diaSemana(Integer.parseInt(partes[0]))
                            .hora(Integer.parseInt(partes[1]))
                            .cantidad(entrada.getValue())
                            .build();
                })
                .sorted(Comparator.comparingInt(OcupacionFranja::getDiaSemana).thenComparingInt(OcupacionFranja::getHora))
                .toList();
    }

    private List<CanchaUso> calcularCanchasMasUsadas(List<Reserva> ocupadas) {
        Map<String, Long> porCancha = ocupadas.stream()
                .filter(reserva -> reserva.getCancha() != null && reserva.getCancha().getNombre() != null)
                .collect(Collectors.groupingBy(reserva -> reserva.getCancha().getNombre(), Collectors.counting()));
        return porCancha.entrySet().stream()
                .map(entrada -> CanchaUso.builder().canchaNombre(entrada.getKey()).reservas(entrada.getValue()).build())
                .sorted(Comparator.comparingLong(CanchaUso::getReservas).reversed())
                .toList();
    }

    private List<IngresoMes> calcularIngresosPorMes(LocalDate hoy, List<Reserva> ocupadas, List<SolicitudInscripcion> solicitudes) {
        List<IngresoMes> ingresos = new ArrayList<>();
        YearMonth actual = YearMonth.from(hoy);
        for (int i = 5; i >= 0; i--) {
            YearMonth mes = actual.minusMonths(i);
            BigDecimal turnos = ocupadas.stream()
                    .filter(reserva -> YearMonth.from(reserva.getFecha()).equals(mes))
                    .map(reserva -> reserva.getCancha() != null && reserva.getCancha().getPrecioPorHora() != null
                            ? reserva.getCancha().getPrecioPorHora()
                            : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal inscripciones = solicitudes.stream()
                    .filter(solicitud -> solicitud.getEstado() == EstadoSolicitud.APROBADA
                            && solicitud.getCreadoEn() != null
                            && YearMonth.from(solicitud.getCreadoEn()).equals(mes))
                    .map(this::ingresoSolicitud)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            ingresos.add(IngresoMes.builder().mes(mes.toString()).turnos(turnos).inscripciones(inscripciones).build());
        }
        return ingresos;
    }

    private List<EmbudoTorneo> calcularEmbudo(Long lugarId, List<SolicitudInscripcion> solicitudes) {
        List<Torneo> torneos = torneoRepository.findByActivoTrueAndEstadoIn(TORNEOS_ABIERTOS).stream()
                .filter(torneo -> deLugarTorneo(torneo, lugarId))
                .toList();
        return torneos.stream()
                .map(torneo -> {
                    long inscriptos = parejaRepository.countByTorneoId(torneo.getId());
                    BigDecimal ingresos = solicitudes.stream()
                            .filter(solicitud -> solicitud.getEstado() == EstadoSolicitud.APROBADA
                                    && solicitud.getTorneo().getId().equals(torneo.getId()))
                            .map(this::ingresoSolicitud)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    Integer cupo = torneo.getCupoMaximoParejas() != null
                            ? torneo.getCupoMaximoParejas()
                            : torneo.getCantidadParejasObjetivo();
                    return EmbudoTorneo.builder()
                            .torneoId(torneo.getId())
                            .torneoNombre(torneo.getNombre())
                            .inscriptos(inscriptos)
                            .cupo(cupo)
                            .ingresos(ingresos)
                            .build();
                })
                .sorted(Comparator.comparingLong(EmbudoTorneo::getInscriptos).reversed())
                .toList();
    }

    private List<CategoriaDemanda> calcularCategoriasDemandadas(List<SolicitudInscripcion> solicitudes) {
        Map<String, Long> porCategoria = solicitudes.stream()
                .filter(solicitud -> solicitud.getEstado() == EstadoSolicitud.APROBADA && solicitud.getCategoria() != null)
                .collect(Collectors.groupingBy(solicitud -> solicitud.getCategoria().getNombre(), Collectors.counting()));
        return porCategoria.entrySet().stream()
                .map(entrada -> CategoriaDemanda.builder().categoriaNombre(entrada.getKey()).inscriptos(entrada.getValue()).build())
                .sorted(Comparator.comparingLong(CategoriaDemanda::getInscriptos).reversed())
                .toList();
    }

    private BigDecimal ingresoSolicitud(SolicitudInscripcion solicitud) {
        BigDecimal costo = solicitud.getTorneo().getCostoInscripcionJugador();
        return costo != null ? costo.multiply(BigDecimal.valueOf(2)) : BigDecimal.ZERO;
    }

    private boolean deLugar(Reserva reserva, Long lugarId) {
        if (lugarId == null) return true;
        return reserva.getCancha() != null && reserva.getCancha().getLugar() != null
                && lugarId.equals(reserva.getCancha().getLugar().getId());
    }

    private boolean deLugarTorneo(Torneo torneo, Long lugarId) {
        if (lugarId == null) return true;
        return torneo.getLugar() != null && lugarId.equals(torneo.getLugar().getId());
    }
}
