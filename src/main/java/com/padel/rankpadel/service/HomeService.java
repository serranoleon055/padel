package com.padel.rankpadel.service;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.response.AdminDashboardResponse;
import com.padel.rankpadel.dto.response.HomeResponse;
import com.padel.rankpadel.dto.response.HomeSummaryResponse;
import com.padel.rankpadel.dto.response.PartidoResponse;
import com.padel.rankpadel.dto.response.RankingResponse;
import com.padel.rankpadel.dto.response.TemporadaResponse;
import com.padel.rankpadel.dto.response.TorneoResponse;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.Temporada;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.enums.EstadoTorneo;
import com.padel.rankpadel.mapper.PartidoMapper;
import com.padel.rankpadel.mapper.TemporadaMapper;
import com.padel.rankpadel.mapper.TorneoMapper;
import com.padel.rankpadel.repository.CategoriaRepository;
import com.padel.rankpadel.repository.JugadorRepository;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.TemporadaRepository;
import com.padel.rankpadel.repository.TorneoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HomeService {

    private static final List<EstadoTorneo> ESTADOS_PUBLICOS_ACTIVOS = List.of(
            EstadoTorneo.INSCRIPCION,
            EstadoTorneo.SORTEADO,
            EstadoTorneo.EN_CURSO);

    private final TorneoRepository torneoRepository;
    private final CategoriaRepository categoriaRepository;
    private final JugadorRepository jugadorRepository;
    private final PartidoRepository partidoRepository;
    private final ParejaRepository parejaRepository;
    private final TorneoMapper torneoMapper;
    private final PartidoMapper partidoMapper;
    private final TemporadaRepository temporadaRepository;
    private final TemporadaMapper temporadaMapper;
    private final RankingService rankingService;

    @Transactional(readOnly = true)
    public HomeSummaryResponse obtenerSummary() {
        int anioActual = LocalDate.now().getYear();
        long torneosDelAnio = torneoRepository.findByActivoTrue().stream()
                .filter(torneo -> torneo.getFechaInicio() != null && torneo.getFechaInicio().getYear() == anioActual)
                .count();

        return HomeSummaryResponse.builder()
                .torneosActivos(torneosDelAnio)
                .jugadoresRegistrados(jugadorRepository.countByActivoTrue())
                .partidosFinalizados(partidoRepository.countByEstado(EstadoPartido.FINALIZADO))
                .partidosEnVivo(partidoRepository.countByEstado(EstadoPartido.EN_CURSO))
                .torneosTotales(torneoRepository.countByActivoTrue())
                .categoriasActivas(categoriaRepository.count())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse obtenerDashboard() {
        HomeSummaryResponse summary = obtenerSummary();

        TemporadaResponse temporadaActiva = null;
        List<Temporada> temporadas = temporadaRepository.findAll();
        for (Temporada t : temporadas) {
            if (t.isActiva()) {
                temporadaActiva = temporadaMapper.temporadaToResponse(t);
                break;
            }
        }

        List<Torneo> todos = torneoRepository.findByActivoTrue();
        List<TorneoResponse> ultimosTorneos = todos.stream()
                .sorted(Comparator.comparing(Torneo::getId).reversed())
                .limit(5)
                .map(this::mapearTorneoConMetricas)
                .toList();

        List<TorneoResponse> torneosEnVivo = todos.stream()
                .filter(t -> EstadoTorneo.EN_CURSO.equals(t.getEstado()))
                .limit(4)
                .map(this::mapearTorneoConMetricas)
                .toList();

        List<Long> evolucionMeses = new ArrayList<>();
        LocalDate now = LocalDate.now();
        for (int i = 5; i >= 0; i--) {
            LocalDate inicioMes = now.minusMonths(i).withDayOfMonth(1);
            LocalDate finMes = now.minusMonths(i).with(TemporalAdjusters.lastDayOfMonth());
            long count = todos.stream()
                    .filter(t -> t.getFechaInicio() != null
                            && !t.getFechaInicio().isBefore(inicioMes)
                            && !t.getFechaInicio().isAfter(finMes))
                    .count();
            evolucionMeses.add(count);
        }

        return AdminDashboardResponse.builder()
                .summary(summary)
                .temporadaActiva(temporadaActiva)
                .ultimosTorneos(ultimosTorneos)
                .torneosEnVivo(torneosEnVivo)
                .evolucionMeses(evolucionMeses)
                .build();
    }

    @Transactional(readOnly = true)
    public HomeResponse obtenerHome() {
        List<Torneo> torneos = torneoRepository.findByActivoTrue();
        List<Partido> resultados = partidoRepository.findTop10ByEstadoOrderByFechaHoraDescIdDesc(EstadoPartido.FINALIZADO);

        List<TorneoResponse> proximosTorneos = torneos.stream()
                .filter(this::esTorneoVisibleComoProximo)
                .sorted(compararPorFecha())
                .limit(6)
                .map(this::mapearTorneoConMetricas)
                .toList();

        List<TorneoResponse> torneosEnVivo = torneos.stream()
                .filter(torneo -> EstadoTorneo.EN_CURSO.equals(torneo.getEstado()))
                .sorted(compararPorFecha())
                .limit(4)
                .map(this::mapearTorneoConMetricas)
                .toList();

        List<PartidoResponse> ultimosResultados = resultados.stream()
                .map(partidoMapper::partidoToResponse)
                .toList();

        List<PartidoResponse> ultimosCampeones = partidoRepository.findUltimasFinales().stream()
                .limit(5)
                .map(partidoMapper::partidoToResponse)
                .toList();

        List<RankingResponse> rankingDestacado = rankingService.obtenerRanking(null, null).stream()
                .limit(5)
                .toList();

        return HomeResponse.builder()
                .summary(obtenerSummary())
                .torneoDestacado(obtenerTorneoDestacado(torneos))
                .proximosTorneos(proximosTorneos)
                .torneosEnVivo(torneosEnVivo)
                .partidosEnVivo(listarPartidosEnVivo())
                .ultimosResultados(ultimosResultados)
                .ultimosCampeones(ultimosCampeones)
                .rankingDestacado(rankingDestacado)
                .build();
    }

    private TorneoResponse obtenerTorneoDestacado(List<Torneo> torneos) {
        return torneos.stream()
                .filter(this::esTorneoPublicoDestacable)
                .sorted(Comparator.comparing(this::sinCategorias)
                        .thenComparingInt(this::prioridadDestacado)
                        .thenComparing(compararPorFecha()))
                .findFirst()
                .map(this::mapearTorneoConMetricas)
                .orElse(null);
    }

    private List<PartidoResponse> listarPartidosEnVivo() {
        return partidoRepository.findTop10ByEstadoOrderByFechaHoraDescIdDesc(EstadoPartido.EN_CURSO).stream()
                .map(partidoMapper::partidoToResponse)
                .toList();
    }

    private TorneoResponse mapearTorneoConMetricas(Torneo torneo) {
        TorneoResponse response = torneoMapper.torneoToResponse(torneo);
        response.setCantidadParejas(parejaRepository.countByTorneoId(torneo.getId()));
        response.setCantidadPartidos(partidoRepository.countByTorneoId(torneo.getId()));
        response.setPartidosFinalizados(partidoRepository.countByTorneoIdAndEstado(
                torneo.getId(),
                EstadoPartido.FINALIZADO));
        return response;
    }

    private boolean esTorneoVisibleComoProximo(Torneo torneo) {
        return ESTADOS_PUBLICOS_ACTIVOS.contains(torneo.getEstado());
    }

    private boolean esTorneoPublicoDestacable(Torneo torneo) {
        return ESTADOS_PUBLICOS_ACTIVOS.contains(torneo.getEstado());
    }

    private boolean esFinalConCampeon(Partido partido) {
        return partido.getGanador() != null
                && partido.getRonda() != null
                && partido.getRonda().getNombre() != null
                && partido.getRonda().getNombre().equalsIgnoreCase("Final");
    }

    private int prioridadDestacado(Torneo torneo) {
        if (EstadoTorneo.EN_CURSO.equals(torneo.getEstado())) {
            return 0;
        }
        if (EstadoTorneo.INSCRIPCION.equals(torneo.getEstado())) {
            return 1;
        }
        if (EstadoTorneo.SORTEADO.equals(torneo.getEstado())) {
            return 2;
        }
        return 3;
    }

    private boolean sinCategorias(Torneo torneo) {
        return torneo.getCategorias() == null || torneo.getCategorias().isEmpty();
    }

    private Comparator<Torneo> compararPorFecha() {
        return Comparator.comparing(Torneo::getFechaInicio, Comparator.nullsLast(Comparator.naturalOrder()));
    }

}
