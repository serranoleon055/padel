package com.padel.rankpadel.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    private static final List<EstadoTorneo> ESTADOS_TORNEO_JUGADO = List.of(EstadoTorneo.EN_CURSO,
            EstadoTorneo.FINALIZADO);

    public void actualizarRanking(Partido partido) {

        if (!partido.getTorneo().isSumaPuntosRanking())
            return;

        String nombreRonda = partido.getFase().equals(FasePartido.GRUPOS)
                ? "Grupos"
                : partido.getRonda().getNombre();

        Optional<ConfiguracionPuntos> config = buscarConfigPorRonda(partido.getTorneo().getId(), nombreRonda);

        int puntosGanador = config.map(ConfiguracionPuntos::getPuntosGanador).orElse(0);
        int puntosPerdedor = config.map(ConfiguracionPuntos::getPuntosPerdedor).orElse(0);

        Pareja ganador = partido.getGanador();
        Pareja perdedor = ganador.getId().equals(partido.getLocal().getId())
                ? partido.getVisitante()
                : partido.getLocal();

        Optional<Temporada> temporadaActiva = temporadaRepository.findFirstByActivaTrue();

        asignarPuntos(ganador.getJugador1(), ganador.getCategoria(), partido.getTorneo(), puntosGanador, true, null);
        asignarPuntos(ganador.getJugador2(), ganador.getCategoria(), partido.getTorneo(), puntosGanador, true, null);
        asignarPuntos(perdedor.getJugador1(), perdedor.getCategoria(), partido.getTorneo(), puntosPerdedor, false,
                null);
        asignarPuntos(perdedor.getJugador2(), perdedor.getCategoria(), partido.getTorneo(), puntosPerdedor, false,
                null);

        temporadaActiva.ifPresent(temporada -> {
            asignarPuntos(ganador.getJugador1(), ganador.getCategoria(), partido.getTorneo(), puntosGanador, true,
                    temporada);
            asignarPuntos(ganador.getJugador2(), ganador.getCategoria(), partido.getTorneo(), puntosGanador, true,
                    temporada);
            asignarPuntos(perdedor.getJugador1(), perdedor.getCategoria(), partido.getTorneo(), puntosPerdedor, false,
                    temporada);
            asignarPuntos(perdedor.getJugador2(), perdedor.getCategoria(), partido.getTorneo(), puntosPerdedor, false,
                    temporada);
            recalcularPosiciones(ganador.getCategoria(), temporada);
        });

        recalcularPosiciones(ganador.getCategoria(), null);
    }

    public List<RankingResponse> obtenerRanking(Long categoriaId, Genero genero) {
        Optional<Temporada> temporadaActiva = temporadaRepository.findFirstByActivaTrue();

        List<RankingEntry> entradas;

        if (temporadaActiva.isPresent()) {
            Long temporadaId = temporadaActiva.get().getId();
            if (categoriaId != null) {
                entradas = rankingEntryRepository.findByCategoriaIdAndTemporadaId(categoriaId, temporadaId);
            } else if (genero != null) {
                entradas = rankingEntryRepository.findByCategoriaGeneroAndTemporadaId(genero, temporadaId);
            } else {
                entradas = rankingEntryRepository.findByTemporadaId(temporadaId);
            }
        } else {
            if (categoriaId != null) {
                entradas = rankingEntryRepository.findByCategoriaIdAndTemporadaIsNull(categoriaId);
            } else if (genero != null) {
                entradas = rankingEntryRepository.findByCategoriaGeneroAndTemporadaIsNull(genero);
            } else {
                entradas = rankingEntryRepository.findByTemporadaIsNull();
            }
        }

        if (entradas.isEmpty() && temporadaActiva.isPresent()) {
            if (categoriaId != null) {
                entradas = rankingEntryRepository.findByCategoriaIdAndTemporadaIsNull(categoriaId);
            } else if (genero != null) {
                entradas = rankingEntryRepository.findByCategoriaGeneroAndTemporadaIsNull(genero);
            } else {
                entradas = rankingEntryRepository.findByTemporadaIsNull();
            }
        }

        entradas.sort(ORDEN_RANKING);

        List<RankingResponse> respuestas = new ArrayList<>();
        for (RankingEntry e : entradas) {
            Jugador jugador = obtenerJugadorActivo(e);
            if (jugador == null)
                continue;
            respuestas.add(RankingResponse.builder()
                    .posicion(respuestas.size() + 1)
                    .jugadorId(jugador.getId())
                    .jugadorNombre(jugador.getNombre() + " " + jugador.getApellido())
                    .jugadorFotoUrl(jugador.getFotoUrl())
                    .categoriaId(e.getCategoria().getId())
                    .categoriaNombre(e.getCategoria().getNombre())
                    .puntosTotales(e.getPuntosTotales())
                    .torneosJugados(contarTorneosJugados(jugador, e))
                    .victorias(e.getVictorias())
                    .derrotas(e.getDerrotas())
                    .tendencia(formatearTendencia(e))
                    .build());
        }

        return respuestas;
    }

    public int recalcularPuntos() {
        List<RankingEntry> entradas = rankingEntryRepository.findAll();
        for (RankingEntry entrada : entradas) {
            entrada.setPuntosTotales(0);
        }
        rankingEntryRepository.saveAll(entradas);

        Optional<Temporada> temporadaActiva = temporadaRepository.findFirstByActivaTrue();
        List<Partido> partidos = partidoRepository.findPartidosQueSumanPuntos();
        Set<Categoria> categoriasAfectadas = new HashSet<>();

        for (Partido partido : partidos) {
            String nombreRonda = partido.getFase().equals(FasePartido.GRUPOS)
                    ? "Grupos"
                    : partido.getRonda().getNombre();

            Optional<ConfiguracionPuntos> config = buscarConfigPorRonda(partido.getTorneo().getId(), nombreRonda);
            int puntosGanador = config.map(ConfiguracionPuntos::getPuntosGanador).orElse(0);
            int puntosPerdedor = config.map(ConfiguracionPuntos::getPuntosPerdedor).orElse(0);

            Pareja ganador = partido.getGanador();
            Pareja perdedor = ganador.getId().equals(partido.getLocal().getId())
                    ? partido.getVisitante()
                    : partido.getLocal();

            sumarPuntos(ganador, puntosGanador, null);
            sumarPuntos(perdedor, puntosPerdedor, null);
            temporadaActiva.ifPresent(temporada -> {
                sumarPuntos(ganador, puntosGanador, temporada);
                sumarPuntos(perdedor, puntosPerdedor, temporada);
            });

            categoriasAfectadas.add(ganador.getCategoria());
        }

        for (Categoria categoria : categoriasAfectadas) {
            recalcularPosiciones(categoria, null);
            temporadaActiva.ifPresent(temporada -> recalcularPosiciones(categoria, temporada));
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
            entrada.setPuntosTotales(entrada.getPuntosTotales() + puntos);
            rankingEntryRepository.save(entrada);
        }
    }

    private Optional<ConfiguracionPuntos> buscarConfigPorRonda(Long torneoId, String nombreRonda) {
        String clave = NormalizadorRonda.normalizar(nombreRonda);
        return configuracionPuntosRepository.findByTorneoIdOrderByOrden(torneoId).stream()
                .filter(config -> NormalizadorRonda.normalizar(config.getNombreRonda()).equals(clave))
                .findFirst();
    }

    private int contarTorneosJugados(Jugador jugador, RankingEntry entrada) {
        Long jugadorId = jugador.getId();
        Long categoriaId = entrada.getCategoria().getId();
        if (entrada.getTemporada() != null) {
            return (int) parejaRepository.contarTorneosJugadosPorTemporada(
                    jugadorId, categoriaId, entrada.getTemporada().getId(), ESTADOS_TORNEO_JUGADO);
        }
        return (int) parejaRepository.contarTorneosJugados(jugadorId, categoriaId, ESTADOS_TORNEO_JUGADO);
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

        entrada.setPuntosTotales(entrada.getPuntosTotales() + puntos);
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
        List<Pareja> parejas = parejaRepository.findByTorneoId(torneoId);
        Set<Long> jugadoresVistos = new HashSet<>();
        Optional<Temporada> temporadaActiva = temporadaRepository.findFirstByActivaTrue();

        for (Pareja pareja : parejas) {
            for (Jugador jugador : List.of(pareja.getJugador1(), pareja.getJugador2())) {
                if (jugadoresVistos.contains(jugador.getId()))
                    continue;
                jugadoresVistos.add(jugador.getId());

                rankingEntryRepository
                        .findByJugadorIdAndCategoriaIdAndTemporadaIsNull(jugador.getId(), pareja.getCategoria().getId())
                        .ifPresent(entrada -> {
                            entrada.setTorneosJugados(entrada.getTorneosJugados() + 1);
                            rankingEntryRepository.save(entrada);
                        });

                temporadaActiva.ifPresent(temporada -> rankingEntryRepository
                        .findByJugadorIdAndCategoriaIdAndTemporadaId(jugador.getId(), pareja.getCategoria().getId(),
                                temporada.getId())
                        .ifPresent(entrada -> {
                            entrada.setTorneosJugados(entrada.getTorneosJugados() + 1);
                            rankingEntryRepository.save(entrada);
                        }));
            }
        }
    }

    public void reabrirTorneo(Long torneoId) {
        List<Pareja> parejas = parejaRepository.findByTorneoId(torneoId);
        Set<Long> jugadoresVistos = new HashSet<>();
        Optional<Temporada> temporadaActiva = temporadaRepository.findFirstByActivaTrue();

        for (Pareja pareja : parejas) {
            for (Jugador jugador : List.of(pareja.getJugador1(), pareja.getJugador2())) {
                if (jugadoresVistos.contains(jugador.getId()))
                    continue;
                jugadoresVistos.add(jugador.getId());

                rankingEntryRepository
                        .findByJugadorIdAndCategoriaIdAndTemporadaIsNull(jugador.getId(), pareja.getCategoria().getId())
                        .ifPresent(entrada -> {
                            entrada.setTorneosJugados(Math.max(0, entrada.getTorneosJugados() - 1));
                            rankingEntryRepository.save(entrada);
                        });

                temporadaActiva.ifPresent(temporada -> rankingEntryRepository
                        .findByJugadorIdAndCategoriaIdAndTemporadaId(jugador.getId(), pareja.getCategoria().getId(),
                                temporada.getId())
                        .ifPresent(entrada -> {
                            entrada.setTorneosJugados(Math.max(0, entrada.getTorneosJugados() - 1));
                            rankingEntryRepository.save(entrada);
                        }));
            }
        }
    }

    public void revertirRankingPartido(Partido partido) {
        if (!partido.getTorneo().isSumaPuntosRanking())
            return;
        if (partido.getGanador() == null)
            return;

        String nombreRonda = FasePartido.GRUPOS.equals(partido.getFase())
                ? "Grupos"
                : partido.getRonda().getNombre();

        Optional<ConfiguracionPuntos> config = buscarConfigPorRonda(partido.getTorneo().getId(), nombreRonda);

        int puntosGanador = config.map(ConfiguracionPuntos::getPuntosGanador).orElse(0);
        int puntosPerdedor = config.map(ConfiguracionPuntos::getPuntosPerdedor).orElse(0);

        Pareja ganador = partido.getGanador();
        Pareja perdedor = ganador.getId().equals(partido.getLocal().getId())
                ? partido.getVisitante()
                : partido.getLocal();

        Optional<Temporada> temporadaActiva = temporadaRepository.findFirstByActivaTrue();

        restarPuntos(ganador.getJugador1(), ganador.getCategoria(), puntosGanador, true, null);
        restarPuntos(ganador.getJugador2(), ganador.getCategoria(), puntosGanador, true, null);
        restarPuntos(perdedor.getJugador1(), perdedor.getCategoria(), puntosPerdedor, false, null);
        restarPuntos(perdedor.getJugador2(), perdedor.getCategoria(), puntosPerdedor, false, null);

        final int pG = puntosGanador;
        final int pP = puntosPerdedor;
        final Pareja g = ganador;
        final Pareja p = perdedor;
        temporadaActiva.ifPresent(temporada -> {
            restarPuntos(g.getJugador1(), g.getCategoria(), pG, true, temporada);
            restarPuntos(g.getJugador2(), g.getCategoria(), pG, true, temporada);
            restarPuntos(p.getJugador1(), p.getCategoria(), pP, false, temporada);
            restarPuntos(p.getJugador2(), p.getCategoria(), pP, false, temporada);
            recalcularPosiciones(g.getCategoria(), temporada);
        });

        recalcularPosiciones(ganador.getCategoria(), null);
    }

    public void revertirRankingTorneo(Torneo torneo, List<Partido> partidos, List<Pareja> parejas) {
        if (!torneo.isSumaPuntosRanking())
            return;

        Optional<Temporada> temporadaActiva = temporadaRepository.findFirstByActivaTrue();
        Set<Categoria> categoriasAfectadas = new HashSet<>();

        for (Partido partido : partidos) {
            if (partido.getEstado() != EstadoPartido.FINALIZADO || partido.getGanador() == null)
                continue;

            String nombreRonda = FasePartido.GRUPOS.equals(partido.getFase())
                    ? "Grupos"
                    : partido.getRonda().getNombre();

            Optional<ConfiguracionPuntos> config = buscarConfigPorRonda(torneo.getId(), nombreRonda);

            int puntosGanador = config.map(ConfiguracionPuntos::getPuntosGanador).orElse(0);
            int puntosPerdedor = config.map(ConfiguracionPuntos::getPuntosPerdedor).orElse(0);

            Pareja ganador = partido.getGanador();
            Pareja perdedor = ganador.getId().equals(partido.getLocal().getId())
                    ? partido.getVisitante()
                    : partido.getLocal();

            restarPuntos(ganador.getJugador1(), ganador.getCategoria(), puntosGanador, true, null);
            restarPuntos(ganador.getJugador2(), ganador.getCategoria(), puntosGanador, true, null);
            restarPuntos(perdedor.getJugador1(), perdedor.getCategoria(), puntosPerdedor, false, null);
            restarPuntos(perdedor.getJugador2(), perdedor.getCategoria(), puntosPerdedor, false, null);
            categoriasAfectadas.add(ganador.getCategoria());

            final int pG = puntosGanador;
            final int pP = puntosPerdedor;
            final Pareja g = ganador;
            final Pareja p = perdedor;
            temporadaActiva.ifPresent(temporada -> {
                restarPuntos(g.getJugador1(), g.getCategoria(), pG, true, temporada);
                restarPuntos(g.getJugador2(), g.getCategoria(), pG, true, temporada);
                restarPuntos(p.getJugador1(), p.getCategoria(), pP, false, temporada);
                restarPuntos(p.getJugador2(), p.getCategoria(), pP, false, temporada);
            });
        }

        if (torneo.getEstado() == EstadoTorneo.FINALIZADO) {
            Set<Long> vistos = new HashSet<>();
            for (Pareja pareja : parejas) {
                for (Jugador jugador : List.of(pareja.getJugador1(), pareja.getJugador2())) {
                    if (!vistos.add(jugador.getId()))
                        continue;
                    rankingEntryRepository
                            .findByJugadorIdAndCategoriaIdAndTemporadaIsNull(jugador.getId(),
                                    pareja.getCategoria().getId())
                            .ifPresent(e -> {
                                e.setTorneosJugados(Math.max(0, e.getTorneosJugados() - 1));
                                rankingEntryRepository.save(e);
                            });
                    temporadaActiva.ifPresent(temporada -> rankingEntryRepository
                            .findByJugadorIdAndCategoriaIdAndTemporadaId(jugador.getId(), pareja.getCategoria().getId(),
                                    temporada.getId())
                            .ifPresent(e -> {
                                e.setTorneosJugados(Math.max(0, e.getTorneosJugados() - 1));
                                rankingEntryRepository.save(e);
                            }));
                }
            }
        }

        for (Categoria categoria : categoriasAfectadas) {
            recalcularPosiciones(categoria, null);
            temporadaActiva.ifPresent(temporada -> recalcularPosiciones(categoria, temporada));
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
            entrada.setPuntosTotales(Math.max(0, entrada.getPuntosTotales() - puntos));
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
            int posicionAnterior = entrada.getPosicionActual() == 0
                    ? nuevaPosicion
                    : entrada.getPosicionActual();
            entrada.setPosicionAnterior(posicionAnterior);
            entrada.setPosicionActual(nuevaPosicion);
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
