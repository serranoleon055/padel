package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.response.AdminDashboardResponse;
import com.padel.rankpadel.dto.response.CampeonResponse;
import com.padel.rankpadel.dto.response.CanchaEstadoDashboardResponse;
import com.padel.rankpadel.dto.response.SlotDisponibilidad;
import com.padel.rankpadel.dto.response.HomeResponse;
import com.padel.rankpadel.dto.response.HomeSummaryResponse;
import com.padel.rankpadel.dto.response.PagedResponse;
import com.padel.rankpadel.dto.response.PartidoResponse;
import com.padel.rankpadel.dto.response.RankingResponse;
import com.padel.rankpadel.dto.response.ReservaResponse;
import com.padel.rankpadel.dto.response.SolicitudInscripcionResponse;
import com.padel.rankpadel.dto.response.TemporadaResponse;
import com.padel.rankpadel.dto.response.TorneoResponse;
import com.padel.rankpadel.dto.response.TurnoResumenResponse;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.entity.Temporada;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.enums.EstadoTorneo;
import com.padel.rankpadel.enums.Genero;
import com.padel.rankpadel.mapper.PartidoMapper;
import com.padel.rankpadel.mapper.TemporadaMapper;
import com.padel.rankpadel.mapper.TorneoMapper;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.CategoriaRepository;
import com.padel.rankpadel.repository.JugadorRepository;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.ReservaRepository;
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
    private final CampeonService campeonService;
    private final ReservaRepository reservaRepository;
    private final CanchaRepository canchaRepository;
    private final InscripcionService inscripcionService;
    private final DisponibilidadCanchaService disponibilidadCanchaService;

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
                .categoriasActivas(categoriaRepository.count())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse obtenerDashboard(Long lugarId) {
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
        List<Torneo> torneosDelLugar = todos.stream()
                .filter(t -> lugarId == null || (t.getLugar() != null && lugarId.equals(t.getLugar().getId())))
                .toList();
        List<TorneoResponse> ultimosTorneos = torneosDelLugar.stream()
                .sorted(Comparator.comparing(Torneo::getId).reversed())
                .limit(5)
                .map(this::mapearTorneoConMetricas)
                .toList();

        List<TorneoResponse> torneosEnVivo = torneosDelLugar.stream()
                .filter(t -> EstadoTorneo.EN_CURSO.equals(t.getEstado()))
                .limit(4)
                .map(this::mapearTorneoConMetricas)
                .toList();

        LocalDate hoy = LocalDate.now();
        LocalTime ahora = LocalTime.now();

        List<Cancha> canchasLugar = canchaRepository.findByActivoTrue().stream()
                .filter(cancha -> cancha.getLugar() != null && !cancha.getLugar().isArchivado())
                .filter(cancha -> lugarId == null || lugarId.equals(cancha.getLugar().getId()))
                .sorted(Comparator.comparing(Cancha::getNombre, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        java.util.Set<Long> canchasValidas = canchasLugar.stream()
                .map(Cancha::getId)
                .collect(java.util.stream.Collectors.toSet());
        long canchasTotales = canchasValidas.size();

        List<Reserva> reservasConfirmadasHoy = reservaRepository
                .findByFechaAndEstadoIn(hoy, List.of(EstadoReserva.CONFIRMADA, EstadoReserva.FINALIZADA)).stream()
                .filter(reserva -> esDeCancha(reserva, canchasValidas))
                .toList();
        java.util.Set<Long> canchasOcupadasIds = reservasConfirmadasHoy.stream()
                .filter(reserva -> turnoCubreMomento(reserva, ahora))
                .map(reserva -> reserva.getCancha() != null ? reserva.getCancha().getId() : null)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        long canchasOcupadasAhora = canchasOcupadasIds.size();
        long canchasLibresAhora = Math.max(0, canchasTotales - canchasOcupadasAhora);

        List<CanchaEstadoDashboardResponse> canchas = canchasLugar.stream()
                .map(cancha -> CanchaEstadoDashboardResponse.builder()
                        .id(cancha.getId())
                        .nombre(cancha.getNombre())
                        .ocupadaAhora(canchasOcupadasIds.contains(cancha.getId()))
                        .build())
                .toList();

        long turnosDisponiblesHoy = canchasLugar.stream()
                .mapToLong(cancha -> disponibilidadCanchaService.slots(cancha.getId(), hoy).stream()
                        .filter(SlotDisponibilidad::isDisponible)
                        .count())
                .sum();

        List<Reserva> reservasPendientesLista = reservaRepository.findByEstado(EstadoReserva.PENDIENTE).stream()
                .filter(reserva -> esDeCancha(reserva, canchasValidas))
                .sorted(Comparator.comparing(Reserva::getFecha, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Reserva::getHoraInicio, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        long reservasPendientes = reservasPendientesLista.size();

        List<SolicitudInscripcionResponse> solicitudesPendientesLista = inscripcionService.listarPendientesGlobal(lugarId);
        long solicitudesPendientes = solicitudesPendientesLista.size();

        BigDecimal ingresoEstimadoHoy = reservasConfirmadasHoy.stream()
                .map(reserva -> reserva.getCancha() != null ? reserva.getCancha().getPrecioPorHora() : null)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate inicioSemana = hoy.with(DayOfWeek.MONDAY);
        List<Reserva> reservasSemana = reservaRepository.findByFechaBetweenAndEstadoIn(
                inicioSemana, inicioSemana.plusDays(6),
                List.of(EstadoReserva.CONFIRMADA, EstadoReserva.FINALIZADA)).stream()
                .filter(reserva -> esDeCancha(reserva, canchasValidas))
                .toList();
        List<Long> turnosPorDiaSemana = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate dia = inicioSemana.plusDays(i);
            turnosPorDiaSemana.add(reservasSemana.stream().filter(reserva -> dia.equals(reserva.getFecha())).count());
        }

        List<TurnoResumenResponse> proximosTurnosHoy = reservasConfirmadasHoy.stream()
                .filter(reserva -> reserva.getHoraInicio() != null && !reserva.getHoraInicio().isBefore(ahora))
                .sorted(Comparator.comparing((Reserva reserva) -> reserva.getCancha() != null ? reserva.getCancha().getNombre() : "",
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Reserva::getHoraInicio))
                .map(reserva -> TurnoResumenResponse.builder()
                        .canchaId(reserva.getCancha() != null ? reserva.getCancha().getId() : null)
                        .canchaNombre(reserva.getCancha() != null ? reserva.getCancha().getNombre() : "Cancha")
                        .horaInicio(reserva.getHoraInicio())
                        .horaFin(reserva.getHoraFin())
                        .clienteNombre(reserva.getClienteNombre())
                        .build())
                .toList();

        return AdminDashboardResponse.builder()
                .summary(summary)
                .temporadaActiva(temporadaActiva)
                .ultimosTorneos(ultimosTorneos)
                .torneosEnVivo(torneosEnVivo)
                .canchasTotales(canchasTotales)
                .canchasOcupadasAhora(canchasOcupadasAhora)
                .canchasLibresAhora(canchasLibresAhora)
                .turnosDisponiblesHoy(turnosDisponiblesHoy)
                .canchas(canchas)
                .reservasHoy(reservasConfirmadasHoy.size())
                .reservasPendientes(reservasPendientes)
                .solicitudesPendientes(solicitudesPendientes)
                .ingresoEstimadoHoy(ingresoEstimadoHoy)
                .turnosPorDiaSemana(turnosPorDiaSemana)
                .proximosTurnosHoy(proximosTurnosHoy)
                .reservasPendientesLista(reservasPendientesLista.stream().map(this::aReservaResponse).toList())
                .solicitudesPendientesLista(solicitudesPendientesLista)
                .build();
    }

    private boolean esDeCancha(Reserva reserva, java.util.Set<Long> canchasValidas) {
        Long canchaId = reserva.getCancha() != null ? reserva.getCancha().getId() : null;
        return canchaId != null && canchasValidas.contains(canchaId);
    }

    private ReservaResponse aReservaResponse(Reserva reserva) {
        return ReservaResponse.builder()
                .id(reserva.getId())
                .canchaId(reserva.getCancha() != null ? reserva.getCancha().getId() : null)
                .canchaNombre(reserva.getCancha() != null ? reserva.getCancha().getNombre() : null)
                .fecha(reserva.getFecha())
                .horaInicio(reserva.getHoraInicio())
                .horaFin(reserva.getHoraFin())
                .estado(reserva.getEstado() != null ? reserva.getEstado().name() : null)
                .clienteNombre(reserva.getClienteNombre())
                .clienteTelefono(reserva.getClienteTelefono())
                .codigo(reserva.getCodigo())
                .build();
    }

    private boolean turnoCubreMomento(Reserva reserva, LocalTime momento) {
        LocalTime inicio = reserva.getHoraInicio();
        LocalTime fin = reserva.getHoraFin();
        if (inicio == null || fin == null) {
            return false;
        }
        if (fin.isAfter(inicio)) {
            return !momento.isBefore(inicio) && momento.isBefore(fin);
        }
        return !momento.isBefore(inicio) || momento.isBefore(fin);
    }

    @Transactional(readOnly = true)
    public PagedResponse<CampeonResponse> obtenerCampeones(Long categoriaId, Genero genero, int pagina, int tamanio) {
        return campeonService.listar(categoriaId, genero, pagina, tamanio);
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

        List<CampeonResponse> ultimosCampeones = campeonService.ultimos(5);

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
        response.setParejasPorCategoria(contarParejasPorCategoria(torneo));
        return response;
    }

    private Map<Long, Long> contarParejasPorCategoria(Torneo torneo) {
        Map<Long, Long> conteos = new HashMap<>();
        if (torneo.getCategorias() == null) {
            return conteos;
        }
        for (Categoria categoria : torneo.getCategorias()) {
            conteos.put(categoria.getId(),
                    parejaRepository.countByTorneoIdAndCategoriaId(torneo.getId(), categoria.getId()));
        }
        return conteos;
    }

    private boolean esTorneoVisibleComoProximo(Torneo torneo) {
        return ESTADOS_PUBLICOS_ACTIVOS.contains(torneo.getEstado());
    }

    private boolean esTorneoPublicoDestacable(Torneo torneo) {
        return ESTADOS_PUBLICOS_ACTIVOS.contains(torneo.getEstado());
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
