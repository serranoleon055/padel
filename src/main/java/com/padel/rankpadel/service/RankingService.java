package com.padel.rankpadel.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.response.RankingResponse;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.ConfiguracionPuntos;
import com.padel.rankpadel.entity.Jugador;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.RankingEntry;
import com.padel.rankpadel.entity.Temporada;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.enums.EstadoTorneo;
import com.padel.rankpadel.enums.FasePartido;
import com.padel.rankpadel.enums.Genero;
import com.padel.rankpadel.repository.ConfiguracionPuntosRepository;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.RankingEntryRepository;
import com.padel.rankpadel.repository.TemporadaRepository;
import com.padel.rankpadel.util.NormalizadorRonda;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RankingService {

    private final RankingEntryRepository rankingEntryRepository;
    private final ConfiguracionPuntosRepository configuracionPuntosRepository;
    private final ParejaRepository parejaRepository;
    private final TemporadaRepository temporadaRepository;
    private final PartidoRepository partidoRepository;

    private static final Comparator<RankingEntry> ORDEN_RANKING = Comparator
            .comparingInt(RankingEntry::getPuntosTotales).reversed()
            .thenComparing(Comparator.comparingInt(RankingEntry::getVictorias).reversed())
            .thenComparingInt(RankingEntry::getDerrotas)
            .thenComparingLong(RankingService::idJugador);

    private static final List<EstadoPartido> ESTADOS_PARTIDO_JUGADO = List.of(EstadoPartido.FINALIZADO,
            EstadoPartido.WALKOVER, EstadoPartido.RETIRO);

    public void actualizarRanking(Partido partido) {
        if (!partido.getTorneo().isSumaPuntosRanking())
            return;

        Categoria categoria = categoriaDePartido(partido);
        if (categoria == null)
            return;

        recalcularRankingCategoria(categoria);
    }

    public void recalcularRankingCategoria(Categoria categoria) {
        Optional<Temporada> temporadaActivaOpt = temporadaRepository.findFirstByActivaTrue();
        if (temporadaActivaOpt.isEmpty()) {
            return;
        }
        Temporada temporada = temporadaActivaOpt.get();

        resetearEntradas(rankingEntryRepository.findByCategoriaIdAndTemporadaId(categoria.getId(), temporada.getId()));

        List<Partido> partidos = ordenarCronologicamente(partidoRepository.findPartidosQueSumanPuntos().stream()
                .filter(partido -> esDeCategoria(partido, categoria.getId()))
                .collect(Collectors.toList()));

        for (Partido partido : partidos) {
            String nombreRonda = partido.getFase().equals(FasePartido.GRUPOS)
                    ? "Grupos"
                    : partido.getRonda().getNombre();

            Optional<ConfiguracionPuntos> config = buscarConfigPorRonda(partido.getTorneo().getId(), categoria.getId(), nombreRonda);
            int puntosGanador = config.map(ConfiguracionPuntos::getPuntosGanador).orElse(0);
            int puntosPerdedor = config.map(ConfiguracionPuntos::getPuntosPerdedor).orElse(0);

            Pareja ganador = partido.getGanador();
            Pareja perdedor = ganador.getId().equals(partido.getLocal().getId())
                    ? partido.getVisitante()
                    : partido.getLocal();

            asignarPuntos(ganador.getJugador1(), ganador.getCategoria(), partido.getTorneo(), puntosGanador, true, temporada);
            asignarPuntos(ganador.getJugador2(), ganador.getCategoria(), partido.getTorneo(), puntosGanador, true, temporada);
            asignarPuntos(perdedor.getJugador1(), perdedor.getCategoria(), partido.getTorneo(), puntosPerdedor, false, temporada);
            asignarPuntos(perdedor.getJugador2(), perdedor.getCategoria(), partido.getTorneo(), puntosPerdedor, false, temporada);
        }

        recalcularPosiciones(categoria, temporada);
    }

    private void resetearEntradas(List<RankingEntry> entradas) {
        for (RankingEntry entrada : entradas) {
            entrada.setPuntosTotales(0);
            entrada.setVictorias(0);
            entrada.setDerrotas(0);
        }
        rankingEntryRepository.saveAll(entradas);
    }

    private Categoria categoriaDePartido(Partido partido) {
        Pareja referencia = partido.getLocal() != null ? partido.getLocal() : partido.getGanador();
        return referencia != null ? referencia.getCategoria() : null;
    }

    private boolean esDeCategoria(Partido partido, Long categoriaId) {
        try {
            Pareja local = partido.getLocal();
            if (local != null && local.getCategoria() != null && categoriaId.equals(local.getCategoria().getId()))
                return true;
            Pareja ganador = partido.getGanador();
            return ganador != null && ganador.getCategoria() != null && categoriaId.equals(ganador.getCategoria().getId());
        } catch (EntityNotFoundException e) {
            return false;
        }
    }

    public List<RankingResponse> obtenerRanking(Long categoriaId, Genero genero) {
        Optional<Temporada> temporadaActiva = temporadaRepository.findFirstByActivaTrue();
        if (temporadaActiva.isEmpty()) {
            return List.of();
        }
        Long temporadaId = temporadaActiva.get().getId();

        List<RankingEntry> entradas;
        if (categoriaId != null) {
            entradas = rankingEntryRepository.findByCategoriaIdAndTemporadaId(categoriaId, temporadaId);
        } else if (genero != null) {
            entradas = rankingEntryRepository.findByCategoriaGeneroAndTemporadaId(genero, temporadaId);
        } else {
            entradas = rankingEntryRepository.findByTemporadaId(temporadaId);
        }

        entradas.sort(ORDEN_RANKING);

        boolean incluirTorneosJugados = categoriaId != null || genero != null;
        Map<String, Integer> conteoTorneos = incluirTorneosJugados ? construirConteoTorneos(entradas) : Map.of();

        List<RankingResponse> respuestas = new ArrayList<>();
        int posicionCompartida = 0;
        Integer puntosPrevios = null;
        for (RankingEntry e : entradas) {
            Jugador jugador = obtenerJugadorActivo(e);
            if (jugador == null)
                continue;
            if (puntosPrevios == null || e.getPuntosTotales() != puntosPrevios) {
                posicionCompartida = respuestas.size() + 1;
                puntosPrevios = e.getPuntosTotales();
            }
            respuestas.add(RankingResponse.builder()
                    .posicion(posicionCompartida)
                    .jugadorId(jugador.getId())
                    .jugadorNombre(jugador.getNombre() + " " + jugador.getApellido())
                    .jugadorFotoUrl(jugador.getFotoUrl())
                    .categoriaId(e.getCategoria().getId())
                    .categoriaNombre(e.getCategoria().getNombre())
                    .puntosTotales(e.getPuntosTotales())
                    .torneosJugados(incluirTorneosJugados
                            ? conteoTorneos.getOrDefault(jugador.getId() + ":" + e.getCategoria().getId(), 0)
                            : e.getTorneosJugados())
                    .victorias(e.getVictorias())
                    .derrotas(e.getDerrotas())
                    .tendencia(formatearTendencia(e))
                    .build());
        }

        return respuestas;
    }

    public int recalcularPuntos() {
        Optional<Temporada> temporadaActivaOpt = temporadaRepository.findFirstByActivaTrue();
        if (temporadaActivaOpt.isEmpty()) {
            return 0;
        }
        Temporada temporada = temporadaActivaOpt.get();

        List<RankingEntry> entradas = rankingEntryRepository.findByTemporadaId(temporada.getId());
        for (RankingEntry entrada : entradas) {
            entrada.setPuntosTotales(0);
        }
        rankingEntryRepository.saveAll(entradas);

        List<Partido> partidos = ordenarCronologicamente(partidoRepository.findPartidosQueSumanPuntos());
        Set<Categoria> categoriasAfectadas = new HashSet<>();

        for (Partido partido : partidos) {
            String nombreRonda = partido.getFase().equals(FasePartido.GRUPOS)
                    ? "Grupos"
                    : partido.getRonda().getNombre();

            Optional<ConfiguracionPuntos> config = buscarConfigPorRonda(partido.getTorneo().getId(), categoriaIdDeGanador(partido), nombreRonda);
            int puntosGanador = config.map(ConfiguracionPuntos::getPuntosGanador).orElse(0);
            int puntosPerdedor = config.map(ConfiguracionPuntos::getPuntosPerdedor).orElse(0);

            Pareja ganador = partido.getGanador();
            Pareja perdedor = ganador.getId().equals(partido.getLocal().getId())
                    ? partido.getVisitante()
                    : partido.getLocal();

            sumarPuntos(ganador, puntosGanador, temporada);
            sumarPuntos(perdedor, puntosPerdedor, temporada);

            categoriasAfectadas.add(ganador.getCategoria());
        }

        for (Categoria categoria : categoriasAfectadas) {
            recalcularPosiciones(categoria, temporada);
        }

        return partidos.size();
    }

    private void sumarPuntos(Pareja pareja, int puntos, Temporada temporada) {
        if (puntos == 0)
            return;
        for (Jugador jugador : List.of(pareja.getJugador1(), pareja.getJugador2())) {
            RankingEntry entrada;
            if (temporada == null) {
                entrada = rankingEntryRepository
                        .findByJugadorIdAndCategoriaIdAndTemporadaIsNull(jugador.getId(), pareja.getCategoria().getId())
                        .orElseGet(() -> nuevaEntrada(jugador, pareja.getCategoria(), null));
            } else {
                entrada = rankingEntryRepository
                        .findByJugadorIdAndCategoriaIdAndTemporadaId(jugador.getId(), pareja.getCategoria().getId(),
                                temporada.getId())
                        .orElseGet(() -> nuevaEntrada(jugador, pareja.getCategoria(), temporada));
            }
            entrada.setPuntosTotales(sumarConPiso(entrada.getPuntosTotales(), puntos));
            rankingEntryRepository.save(entrada);
        }
    }

    private static int sumarConPiso(int actual, int delta) {
        return Math.max(0, actual + delta);
    }

    private static List<Partido> ordenarCronologicamente(List<Partido> partidos) {
        return partidos.stream()
                .sorted(Comparator.comparing(Partido::getFechaHora, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Partido::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    private Optional<ConfiguracionPuntos> buscarConfigPorRonda(Long torneoId, Long categoriaId, String nombreRonda) {
        String clave = NormalizadorRonda.normalizar(nombreRonda);
        return configuracionPuntosRepository.findByTorneoIdAndCategoriaIdOrderByOrden(torneoId, categoriaId).stream()
                .filter(config -> NormalizadorRonda.normalizar(config.getNombreRonda()).equals(clave))
                .findFirst();
    }

    private Long categoriaIdDeGanador(Partido partido) {
        Pareja ganador = partido.getGanador();
        if (ganador == null || ganador.getCategoria() == null) {
            return null;
        }
        return ganador.getCategoria().getId();
    }

    private Map<String, Integer> construirConteoTorneos(List<RankingEntry> entradas) {
        Set<Long> categoriaIds = entradas.stream()
                .map(entrada -> entrada.getCategoria().getId())
                .collect(Collectors.toSet());
        if (categoriaIds.isEmpty()) {
            return Map.of();
        }

        Long temporadaId = entradas.stream()
                .map(RankingEntry::getTemporada)
                .filter(temporada -> temporada != null)
                .map(Temporada::getId)
                .findFirst()
                .orElse(null);

        List<Partido> partidos = temporadaId == null
                ? partidoRepository.findJugadosPorCategorias(categoriaIds, ESTADOS_PARTIDO_JUGADO)
                : partidoRepository.findJugadosPorCategoriasYTemporada(categoriaIds, temporadaId, ESTADOS_PARTIDO_JUGADO);

        Map<String, Set<Long>> torneosPorClave = new HashMap<>();
        for (Partido partido : partidos) {
            Long torneoId = partido.getTorneo().getId();
            acumularConteo(torneosPorClave, partido.getLocal(), categoriaIds, torneoId);
            acumularConteo(torneosPorClave, partido.getVisitante(), categoriaIds, torneoId);
        }

        Map<String, Integer> conteo = new HashMap<>();
        torneosPorClave.forEach((clave, torneos) -> conteo.put(clave, torneos.size()));
        return conteo;
    }

    private void acumularConteo(Map<String, Set<Long>> mapa, Pareja pareja, Set<Long> categoriaIds, Long torneoId) {
        if (pareja == null) {
            return;
        }
        Long categoriaId;
        try {
            categoriaId = pareja.getCategoria() != null ? pareja.getCategoria().getId() : null;
        } catch (EntityNotFoundException e) {
            return;
        }
        if (categoriaId == null || !categoriaIds.contains(categoriaId)) {
            return;
        }
        agregarTorneoJugador(mapa, pareja.getJugador1(), categoriaId, torneoId);
        agregarTorneoJugador(mapa, pareja.getJugador2(), categoriaId, torneoId);
    }

    private void agregarTorneoJugador(Map<String, Set<Long>> mapa, Jugador jugador, Long categoriaId, Long torneoId) {
        if (jugador == null) {
            return;
        }
        Long jugadorId;
        try {
            jugadorId = jugador.getId();
        } catch (EntityNotFoundException e) {
            return;
        }
        if (jugadorId == null) {
            return;
        }
        mapa.computeIfAbsent(jugadorId + ":" + categoriaId, clave -> new HashSet<>()).add(torneoId);
    }

    private void asignarPuntos(Jugador jugador, Categoria categoria, Torneo torneo,
            int puntos, boolean esGanador, Temporada temporada) {

        RankingEntry entrada;
        if (temporada == null) {
            entrada = rankingEntryRepository
                    .findByJugadorIdAndCategoriaIdAndTemporadaIsNull(jugador.getId(), categoria.getId())
                    .orElse(nuevaEntrada(jugador, categoria, null));
        } else {
            entrada = rankingEntryRepository
                    .findByJugadorIdAndCategoriaIdAndTemporadaId(jugador.getId(), categoria.getId(), temporada.getId())
                    .orElse(nuevaEntrada(jugador, categoria, temporada));
        }

        entrada.setPuntosTotales(sumarConPiso(entrada.getPuntosTotales(), puntos));
        if (esGanador)
            entrada.setVictorias(entrada.getVictorias() + 1);
        else
            entrada.setDerrotas(entrada.getDerrotas() + 1);

        rankingEntryRepository.save(entrada);
    }

    private RankingEntry nuevaEntrada(Jugador jugador, Categoria categoria, Temporada temporada) {
        return RankingEntry.builder()
                .jugador(jugador)
                .categoria(categoria)
                .temporada(temporada)
                .puntosTotales(0)
                .torneosJugados(0)
                .victorias(0)
                .derrotas(0)
                .posicionActual(0)
                .posicionAnterior(0)
                .build();
    }

    public void cerrarTorneo(Long torneoId) {
        Optional<Temporada> temporadaActivaOpt = temporadaRepository.findFirstByActivaTrue();
        if (temporadaActivaOpt.isEmpty()) {
            return;
        }
        Temporada temporada = temporadaActivaOpt.get();
        List<Pareja> parejas = parejaRepository.findByTorneoId(torneoId);
        Set<Long> jugadoresVistos = new HashSet<>();

        for (Pareja pareja : parejas) {
            for (Jugador jugador : List.of(pareja.getJugador1(), pareja.getJugador2())) {
                if (jugadoresVistos.contains(jugador.getId()))
                    continue;
                jugadoresVistos.add(jugador.getId());

                rankingEntryRepository
                        .findByJugadorIdAndCategoriaIdAndTemporadaId(jugador.getId(), pareja.getCategoria().getId(),
                                temporada.getId())
                        .ifPresent(entrada -> {
                            entrada.setTorneosJugados(entrada.getTorneosJugados() + 1);
                            rankingEntryRepository.save(entrada);
                        });
            }
        }
    }

    public void reabrirTorneo(Long torneoId) {
        Optional<Temporada> temporadaActivaOpt = temporadaRepository.findFirstByActivaTrue();
        if (temporadaActivaOpt.isEmpty()) {
            return;
        }
        Temporada temporada = temporadaActivaOpt.get();
        List<Pareja> parejas = parejaRepository.findByTorneoId(torneoId);
        Set<Long> jugadoresVistos = new HashSet<>();

        for (Pareja pareja : parejas) {
            for (Jugador jugador : List.of(pareja.getJugador1(), pareja.getJugador2())) {
                if (jugadoresVistos.contains(jugador.getId()))
                    continue;
                jugadoresVistos.add(jugador.getId());

                rankingEntryRepository
                        .findByJugadorIdAndCategoriaIdAndTemporadaId(jugador.getId(), pareja.getCategoria().getId(),
                                temporada.getId())
                        .ifPresent(entrada -> {
                            entrada.setTorneosJugados(Math.max(0, entrada.getTorneosJugados() - 1));
                            rankingEntryRepository.save(entrada);
                        });
            }
        }
    }

    public void revertirRankingPartido(Partido partido) {
        if (!partido.getTorneo().isSumaPuntosRanking())
            return;

        Categoria categoria = categoriaDePartido(partido);
        if (categoria == null)
            return;

        recalcularRankingCategoria(categoria);
    }

    public void revertirRankingTorneo(Torneo torneo, List<Partido> partidos, List<Pareja> parejas) {
        if (!torneo.isSumaPuntosRanking())
            return;

        Optional<Temporada> temporadaActivaOpt = temporadaRepository.findFirstByActivaTrue();
        if (temporadaActivaOpt.isEmpty()) {
            return;
        }
        Temporada temporada = temporadaActivaOpt.get();
        Set<Categoria> categoriasAfectadas = new HashSet<>();

        for (Partido partido : partidos) {
            // Debe espejar findPartidosQueSumanPuntos: FINALIZADO y RETIRO otorgan puntos
            boolean sumoPuntos = partido.getEstado() == EstadoPartido.FINALIZADO
                    || partido.getEstado() == EstadoPartido.RETIRO;
            if (!sumoPuntos || partido.getGanador() == null)
                continue;

            String nombreRonda = FasePartido.GRUPOS.equals(partido.getFase())
                    ? "Grupos"
                    : partido.getRonda().getNombre();

            Optional<ConfiguracionPuntos> config = buscarConfigPorRonda(torneo.getId(), categoriaIdDeGanador(partido), nombreRonda);

            int puntosGanador = config.map(ConfiguracionPuntos::getPuntosGanador).orElse(0);
            int puntosPerdedor = config.map(ConfiguracionPuntos::getPuntosPerdedor).orElse(0);

            Pareja ganador = partido.getGanador();
            Pareja perdedor = ganador.getId().equals(partido.getLocal().getId())
                    ? partido.getVisitante()
                    : partido.getLocal();

            restarPuntos(ganador.getJugador1(), ganador.getCategoria(), puntosGanador, true, temporada);
            restarPuntos(ganador.getJugador2(), ganador.getCategoria(), puntosGanador, true, temporada);
            restarPuntos(perdedor.getJugador1(), perdedor.getCategoria(), puntosPerdedor, false, temporada);
            restarPuntos(perdedor.getJugador2(), perdedor.getCategoria(), puntosPerdedor, false, temporada);
            categoriasAfectadas.add(ganador.getCategoria());
        }

        if (torneo.getEstado() == EstadoTorneo.FINALIZADO) {
            Set<Long> vistos = new HashSet<>();
            for (Pareja pareja : parejas) {
                for (Jugador jugador : List.of(pareja.getJugador1(), pareja.getJugador2())) {
                    if (!vistos.add(jugador.getId()))
                        continue;
                    rankingEntryRepository
                            .findByJugadorIdAndCategoriaIdAndTemporadaId(jugador.getId(), pareja.getCategoria().getId(),
                                    temporada.getId())
                            .ifPresent(e -> {
                                e.setTorneosJugados(Math.max(0, e.getTorneosJugados() - 1));
                                rankingEntryRepository.save(e);
                            });
                }
            }
        }

        for (Categoria categoria : categoriasAfectadas) {
            recalcularPosiciones(categoria, temporada);
        }
    }

    private void restarPuntos(Jugador jugador, Categoria categoria, int puntos, boolean esGanador,
            Temporada temporada) {
        Optional<RankingEntry> optEntrada;
        if (temporada == null) {
            optEntrada = rankingEntryRepository.findByJugadorIdAndCategoriaIdAndTemporadaIsNull(jugador.getId(),
                    categoria.getId());
        } else {
            optEntrada = rankingEntryRepository.findByJugadorIdAndCategoriaIdAndTemporadaId(jugador.getId(),
                    categoria.getId(), temporada.getId());
        }
        optEntrada.ifPresent(entrada -> {
            entrada.setPuntosTotales(sumarConPiso(entrada.getPuntosTotales(), -puntos));
            if (esGanador)
                entrada.setVictorias(Math.max(0, entrada.getVictorias() - 1));
            else
                entrada.setDerrotas(Math.max(0, entrada.getDerrotas() - 1));
            rankingEntryRepository.save(entrada);
        });
    }

    private void recalcularPosiciones(Categoria categoria, Temporada temporada) {
        List<RankingEntry> entradas;
        if (temporada == null) {
            entradas = rankingEntryRepository.findByCategoriaIdAndTemporadaIsNull(categoria.getId());
        } else {
            entradas = rankingEntryRepository.findByCategoriaIdAndTemporadaId(categoria.getId(), temporada.getId());
        }

        entradas.sort(ORDEN_RANKING);

        for (int i = 0; i < entradas.size(); i++) {
            RankingEntry entrada = entradas.get(i);
            int nuevaPosicion = i + 1;
            if (entrada.getPosicionActual() == 0) {
                entrada.setPosicionAnterior(nuevaPosicion);
                entrada.setPosicionActual(nuevaPosicion);
            } else if (entrada.getPosicionActual() != nuevaPosicion) {
                entrada.setPosicionAnterior(entrada.getPosicionActual());
                entrada.setPosicionActual(nuevaPosicion);
            }
        }

        rankingEntryRepository.saveAll(entradas);
    }

    private String formatearTendencia(RankingEntry entrada) {
        if (entrada.getPosicionAnterior() == 0 || entrada.getPosicionActual() == 0) {
            return "-";
        }
        int diferencia = entrada.getPosicionAnterior() - entrada.getPosicionActual();
        if (diferencia == 0)
            return "-";
        return diferencia > 0 ? "+" + diferencia : String.valueOf(diferencia);
    }

    private static long idJugador(RankingEntry entrada) {
        try {
            Jugador jugador = entrada.getJugador();
            if (jugador == null || jugador.getId() == null) {
                return Long.MAX_VALUE;
            }
            return jugador.getId();
        } catch (EntityNotFoundException e) {
            return Long.MAX_VALUE;
        }
    }

    private Jugador obtenerJugadorActivo(RankingEntry entrada) {
        try {
            Jugador jugador = entrada.getJugador();
            if (jugador == null || !jugador.isActivo())
                return null;
            return jugador;
        } catch (EntityNotFoundException e) {
            return null;
        }
    }
}
