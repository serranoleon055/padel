package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.padel.rankpadel.dto.response.DisponibilidadDuracionResponse;
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
import com.padel.rankpadel.util.OrdenCanchas;
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

        long torneosFinalizados = todos.stream()
                .filter(t -> EstadoTorneo.FINALIZADO.equals(t.getEstado()))
                .count();
        long torneosEnInscripcion = todos.stream()
                .filter(t -> EstadoTorneo.INSCRIPCION.equals(t.getEstado()))
                .count();

        // El panel del mostrador razona en jornadas, no en días de calendario: a la 1 AM
        // de un club que cierra a las 2, "el día" que se está atendiendo empezó ayer.
        // Con `LocalDate.now()`, a esa hora el panel se vaciaba y ofrecía como
        // disponibles los turnos de una jornada que todavía no había empezado.
        LocalDate hoy = disponibilidadCanchaService.fechaDeJornadaActual();

        List<Cancha> canchasLugar = canchaRepository.findByActivoTrue().stream()
                .filter(cancha -> cancha.getLugar() != null && !cancha.getLugar().isArchivado())
                .filter(cancha -> lugarId == null || lugarId.equals(cancha.getLugar().getId()))
                // Por número: como string, "Cancha 10" salía antes que "Cancha 2".
                .sorted(OrdenCanchas.porNombre(Cancha::getNombre))
                .toList();
        java.util.Set<Long> canchasValidas = canchasLugar.stream()
                .map(Cancha::getId)
                .collect(java.util.stream.Collectors.toSet());
        long canchasTotales = canchasValidas.size();

        List<Reserva> reservasConfirmadasHoy = reservaRepository
                .findByFechaAndEstadoIn(hoy, List.of(EstadoReserva.CONFIRMADA, EstadoReserva.FINALIZADA)).stream()
                .filter(reserva -> esDeCancha(reserva, canchasValidas))
                .toList();
        // Para saber qué está ocupado AHORA hace falta mirar la jornada, no el día de
        // calendario: un turno de la 1 AM se guarda con la fecha de ayer, porque pertenece
        // a la sesión que arrancó anoche. Sin la víspera, a la 1:30 el panel mostraba
        // libre una cancha que estaba en uso.
        LocalDateTime momento = LocalDateTime.now();
        java.util.Set<Long> canchasOcupadasIds = reservaRepository
                .findDeLaJornada(hoy, hoy.minusDays(1),
                        List.of(EstadoReserva.CONFIRMADA, EstadoReserva.FINALIZADA)).stream()
                .filter(reserva -> esDeCancha(reserva, canchasValidas))
                .filter(reserva -> turnoEnCurso(reserva, momento))
                .map(reserva -> reserva.getCancha().getId())
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

        // Desglose de lo que queda por vender: la misma hora libre se cuenta una vez por
        // cada duración que el club ofrece, así el mostrador ve "5 de una hora o 2 de
        // dos" en lugar de un número suelto que no dice qué se puede ofrecer.
        Map<Integer, Long> vendiblesPorDuracion = new java.util.TreeMap<>();
        for (Cancha cancha : canchasLugar) {
            disponibilidadCanchaService.turnosVendibles(cancha.getId(), hoy)
                    .forEach((minutos, cantidad) -> vendiblesPorDuracion.merge(minutos, cantidad, Long::sum));
        }
        List<DisponibilidadDuracionResponse> disponiblesPorDuracion = vendiblesPorDuracion.entrySet().stream()
                .map(entrada -> DisponibilidadDuracionResponse.builder()
                        .minutos(entrada.getKey())
                        .turnos(entrada.getValue())
                        .build())
                .toList();
        // La tarjeta resume con el máximo de turnos que todavía entran, que sale de
        // partir los huecos en la duración más corta.
        long turnosDisponiblesHoy = vendiblesPorDuracion.values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0);

        List<Reserva> reservasPendientesLista = reservaRepository.findByEstado(EstadoReserva.PENDIENTE).stream()
                .filter(reserva -> esDeCancha(reserva, canchasValidas))
                .sorted(Comparator.comparing(Reserva::getFecha, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Reserva::getHoraInicio, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        long reservasPendientes = reservasPendientesLista.size();

        List<SolicitudInscripcionResponse> solicitudesPendientesLista = inscripcionService.listarPendientesGlobal(lugarId);
        long solicitudesPendientes = solicitudesPendientesLista.size();

        BigDecimal ingresoEstimadoHoy = reservasConfirmadasHoy.stream()
                .map(Reserva::getPrecioAplicado)
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

        // "Próximo" se mide sobre la jornada, no sobre el reloj: a las 23:40, el turno de
        // las 00:30 todavía no empezó, y comparar LocalTime lo daba por pasado. Por lo
        // mismo, los de la madrugada se ordenan al final de la noche y no al principio.
        List<TurnoResumenResponse> proximosTurnosHoy = reservasConfirmadasHoy.stream()
                .filter(reserva -> reserva.getHoraInicio() != null
                        && !inicioEnJornada(reserva, hoy).isBefore(momento))
                .sorted(OrdenCanchas.<Reserva>porNombre(
                                reserva -> reserva.getCancha() != null ? reserva.getCancha().getNombre() : "")
                        .thenComparing(reserva -> inicioEnJornada(reserva, hoy)))
                .map(reserva -> TurnoResumenResponse.builder()
                        .canchaId(reserva.getCancha() != null ? reserva.getCancha().getId() : null)
                        .canchaNombre(reserva.getCancha() != null ? reserva.getCancha().getNombre() : "Cancha")
                        .horaInicio(reserva.getHoraInicio())
                        .horaFin(reserva.getHoraFin())
                        .clienteNombre(reserva.getClienteNombre())
                        .build())
                .toList();

        return AdminDashboardResponse.builder()
                .fechaJornada(hoy)
                .summary(summary)
                .temporadaActiva(temporadaActiva)
                .ultimosTorneos(ultimosTorneos)
                .torneosEnVivo(torneosEnVivo)
                .canchasTotales(canchasTotales)
                .canchasOcupadasAhora(canchasOcupadasAhora)
                .canchasLibresAhora(canchasLibresAhora)
                .turnosDisponiblesHoy(turnosDisponiblesHoy)
                .disponiblesPorDuracion(disponiblesPorDuracion)
                .canchas(canchas)
                .reservasHoy(reservasConfirmadasHoy.size())
                .reservasPendientes(reservasPendientes)
                .solicitudesPendientes(solicitudesPendientes)
                .torneosFinalizados(torneosFinalizados)
                .torneosEnInscripcion(torneosEnInscripcion)
                .ingresoEstimadoHoy(ingresoEstimadoHoy)
                .turnosPorDiaSemana(turnosPorDiaSemana)
                .proximosTurnosHoy(proximosTurnosHoy)
                .reservasPendientesLista(reservasPendientesLista.stream().map(this::aReservaResponse).toList())
                .solicitudesPendientesLista(solicitudesPendientesLista)
                .build();
    }

    /**
     * Momento real en que arranca un turno dentro de la jornada de {@code fecha}. Un
     * horario anterior a la apertura del club es de la madrugada siguiente.
     */
    private LocalDateTime inicioEnJornada(Reserva reserva, LocalDate fecha) {
        LocalTime apertura = reserva.getCancha() == null
                ? LocalTime.MIN
                : disponibilidadCanchaService.horaApertura(reserva.getCancha().getId());
        return disponibilidadCanchaService.inicioEnSesion(fecha, reserva.getHoraInicio(), apertura);
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

    /**
     * Si el turno se está jugando en este momento. Trabaja con fecha y hora juntas, no con
     * la hora sola: comparar horas no distingue el turno de las 23 de anoche del de las 23
     * de hoy, ni sabe que el de la 1 AM pertenece a la jornada de ayer.
     */
    private boolean turnoEnCurso(Reserva reserva, LocalDateTime momento) {
        if (reserva.getHoraInicio() == null || reserva.getFecha() == null
                || reserva.getCancha() == null) {
            return false;
        }
        LocalDateTime inicio = disponibilidadCanchaService.inicioReal(
                reserva.getCancha().getId(), reserva.getFecha(), reserva.getHoraInicio());
        return !momento.isBefore(inicio)
                && momento.isBefore(inicio.plusMinutes(reserva.getDuracionMin()));
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
