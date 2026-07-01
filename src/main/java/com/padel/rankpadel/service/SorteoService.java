package com.padel.rankpadel.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;

import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.ConfiguracionCategoriaTorneo;
import com.padel.rankpadel.entity.Grupo;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.PosicionGrupo;
import com.padel.rankpadel.entity.RankingEntry;
import com.padel.rankpadel.entity.RondaEliminatorias;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.enums.EstadoTorneo;
import com.padel.rankpadel.enums.FasePartido;
import com.padel.rankpadel.enums.FormatoTorneo;
import com.padel.rankpadel.enums.TipoSorteo;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.ConfiguracionCategoriaTorneoRepository;
import com.padel.rankpadel.repository.GrupoRepository;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.PosicionGrupoRepository;
import com.padel.rankpadel.repository.RankingEntryRepository;
import com.padel.rankpadel.repository.RondaEliminatoriasRepository;
import com.padel.rankpadel.repository.TemporadaRepository;
import com.padel.rankpadel.repository.TorneoRepository;
import com.padel.rankpadel.util.BracketSeeder;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class SorteoService {

    private final TorneoRepository torneoRepository;
    private final ParejaRepository parejaRepository;
    private final GrupoRepository grupoRepository;
    private final PosicionGrupoRepository posicionGrupoRepository;
    private final PartidoRepository partidoRepository;
    private final RondaEliminatoriasRepository rondaEliminatoriasRepository;
    private final RankingEntryRepository rankingEntryRepository;
    private final ConfiguracionCategoriaTorneoRepository configuracionCategoriaTorneoRepository;
    private final TemporadaRepository temporadaRepository;

    public void generarSorteo(Long torneoId) {
        Torneo torneo = torneoRepository.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", torneoId));

        if (!torneo.getEstado().equals(EstadoTorneo.INSCRIPCION)) {
            throw new EstadoInvalidoException("El torneo debe estar en INSCRIPCION para sortearse");
        }

        for (Categoria categoria : torneo.getCategorias()) {
            ConfiguracionCategoriaTorneo config = resolverConfig(torneo, categoria);

            List<Pareja> parejas = parejaRepository
                    .findByTorneoIdAndCategoriaId(torneoId, categoria.getId());

            if (TipoSorteo.ALEATORIO.equals(config.getTipoSorteo())) {
                parejas.forEach(p -> p.setEsCabezaDeSerie(false));
                parejaRepository.saveAll(parejas);
            } else if (TipoSorteo.CABEZAS_SERIE.equals(config.getTipoSorteo())) {
                asignarCabezasDeSerieAutomatico(parejas, categoria.getId(), calcularCantidadCabezas(config, parejas.size()));
            }

            if (parejas.size() < 2) {
                throw new EstadoInvalidoException(
                    "La categoría '" + categoria.getNombre() + "' debe tener al menos 2 parejas para sortear. Actual: " + parejas.size());
            }

            if (!config.isIncluyeFaseGrupos() && !config.isIncluyeEliminacion()) {
                throw new EstadoInvalidoException(
                    "La configuración de la categoría '" + categoria.getNombre() + "' debe incluir fase de grupos o eliminación");
            }

            if (config.getFormato() == FormatoTorneo.LIGA) {
                generarLiga(torneo, categoria, parejas);
                continue;
            }

            if (usaFaseGrupos(config, parejas.size())) {
                generarFaseGrupos(torneo, config, categoria, parejas);
            } else if (config.isIncluyeEliminacion()) {
                generarBracket(torneo, categoria, parejas);
            } else {
                throw new EstadoInvalidoException("No hay una fase disponible para sortear la categoría '" + categoria.getNombre() + "'");
            }
        }

        torneo.setEstado(EstadoTorneo.SORTEADO);
        torneoRepository.save(torneo);
    }

    private ConfiguracionCategoriaTorneo resolverConfig(Torneo torneo, Categoria categoria) {
        return configuracionCategoriaTorneoRepository
                .findByTorneoIdAndCategoriaId(torneo.getId(), categoria.getId())
                .orElseGet(() -> ConfiguracionCategoriaTorneo.builder()
                        .formato(torneo.getFormato())
                        .tipoSorteo(torneo.getTipoSorteo())
                        .cantidadParejasObjetivo(torneo.getCantidadParejasObjetivo())
                        .cantidadGrupos(torneo.getCantidadGrupos())
                        .parejasPorGrupo(torneo.getParejasPorGrupo())
                        .avanzanPorGrupo(torneo.getAvanzanPorGrupo())
                        .incluyeFaseGrupos(torneo.isIncluyeFaseGrupos())
                        .incluyeEliminacion(torneo.isIncluyeEliminacion())
                        .mejorDeSets(torneo.getMejorDeSets())
                        .build());
    }

    private void asignarCabezasDeSerieAutomatico(List<Pareja> parejas, Long categoriaId, int cantidadCabezas) {
        Long temporadaId = temporadaRepository.findFirstByActivaTrue()
                .map(com.padel.rankpadel.entity.Temporada::getId)
                .orElse(null);
        parejas.sort(Comparator.comparingInt((Pareja p) -> {
            int pts1 = puntosJugador(p.getJugador1().getId(), categoriaId, temporadaId);
            int pts2 = puntosJugador(p.getJugador2().getId(), categoriaId, temporadaId);
            return pts1 + pts2;
        }).reversed());

        int seeds = Math.min(cantidadCabezas, parejas.size());
        for (int i = 0; i < parejas.size(); i++) {
            parejas.get(i).setEsCabezaDeSerie(i < seeds);
        }
        parejaRepository.saveAll(parejas);
    }

    private int puntosJugador(Long jugadorId, Long categoriaId, Long temporadaId) {
        if (temporadaId == null) {
            return 0;
        }
        return rankingEntryRepository
                .findByJugadorIdAndCategoriaIdAndTemporadaId(jugadorId, categoriaId, temporadaId)
                .map(RankingEntry::getPuntosTotales).orElse(0);
    }

    private boolean usaFaseGrupos(ConfiguracionCategoriaTorneo config, int cantidadParejas) {
        return config.isIncluyeFaseGrupos()
                && config.getFormato() != FormatoTorneo.ELIMINACION_DIRECTA
                && (cantidadParejas >= 3 || !config.isIncluyeEliminacion());
    }

    private int calcularNumGrupos(ConfiguracionCategoriaTorneo config, int cantidadParejas) {
        Integer configGrupos = config.getCantidadGrupos();
        int minimoGrupos = config.isIncluyeEliminacion() ? 2 : 1;
        int numGrupos = configGrupos != null && configGrupos >= minimoGrupos
                ? configGrupos
                : Math.max(minimoGrupos, (int) Math.ceil((double) cantidadParejas / 4));
        if (numGrupos > cantidadParejas) {
            numGrupos = Math.max(minimoGrupos, (int) Math.ceil((double) cantidadParejas / 4));
        }
        return numGrupos;
    }

    private int calcularCantidadCabezas(ConfiguracionCategoriaTorneo config, int cantidadParejas) {
        if (config.getFormato() == FormatoTorneo.LIGA) {
            return 0;
        }
        if (usaFaseGrupos(config, cantidadParejas)) {
            return calcularNumGrupos(config, cantidadParejas);
        }
        int seeds = Math.max(2, Integer.highestOneBit(Math.max(1, cantidadParejas / 2)));
        return Math.min(8, Math.min(seeds, cantidadParejas));
    }

    private void generarLiga(Torneo torneo, Categoria categoria, List<Pareja> parejas) {
        Grupo grupo = Grupo.builder()
                .nombre("Liga " + categoria.getNombre())
                .torneo(torneo)
                .categoria(categoria)
                .build();
        grupoRepository.save(grupo);

        Collections.shuffle(parejas);

        List<PosicionGrupo> posiciones = new ArrayList<>();
        for (Pareja p : parejas) {
            p.setGrupo(grupo);
            posiciones.add(PosicionGrupo.builder()
                    .grupo(grupo).pareja(p)
                    .posicion(0).pj(0).pg(0).pp(0).puntos(0)
                    .build());
        }
        parejaRepository.saveAll(parejas);
        posicionGrupoRepository.saveAll(posiciones);
        partidoRepository.saveAll(generarPartidosRoundRobin(parejas, grupo, torneo));
    }

    private void generarFaseGrupos(Torneo torneo, ConfiguracionCategoriaTorneo config, Categoria categoria, List<Pareja> parejas) {
        List<Pareja> cabezas = parejas.stream()
                .filter(Pareja::isEsCabezaDeSerie)
                .collect(Collectors.toCollection(ArrayList::new));
        List<Pareja> resto = parejas.stream()
                .filter(p -> !p.isEsCabezaDeSerie())
                .collect(Collectors.toCollection(ArrayList::new));

        int numGrupos = calcularNumGrupos(config, parejas.size());

        List<Grupo> grupos = new ArrayList<>();
        for (int i = 0; i < numGrupos; i++) {
            grupos.add(Grupo.builder()
                    .nombre("Grupo " + (char) ('A' + i))
                    .torneo(torneo)
                    .categoria(categoria)
                    .build());
        }
        grupoRepository.saveAll(grupos);

        Collections.shuffle(cabezas);
        Map<Grupo, List<Pareja>> asignaciones = new HashMap<>();
        grupos.forEach(g -> asignaciones.put(g, new ArrayList<>()));

        for (int i = 0; i < cabezas.size(); i++) {
            Grupo grupo = grupos.get(i % numGrupos);
            cabezas.get(i).setGrupo(grupo);
            asignaciones.get(grupo).add(cabezas.get(i));
        }

        Collections.shuffle(resto);
        for (int i = 0; i < resto.size(); i++) {
            Grupo grupo = grupos.get(i % numGrupos);
            resto.get(i).setGrupo(grupo);
            asignaciones.get(grupo).add(resto.get(i));
        }

        parejaRepository.saveAll(parejas);

        List<PosicionGrupo> posiciones = new ArrayList<>();
        List<Partido> partidos = new ArrayList<>();

        for (Grupo grupo : grupos) {
            List<Pareja> parejasGrupo = asignaciones.get(grupo);

            for (Pareja p : parejasGrupo) {
                posiciones.add(PosicionGrupo.builder()
                        .grupo(grupo)
                        .pareja(p)
                        .posicion(0).pj(0).pg(0).pp(0).puntos(0)
                        .build());
            }

            partidos.addAll(generarPartidosRoundRobin(parejasGrupo, grupo, torneo));
        }

        posicionGrupoRepository.saveAll(posiciones);
        partidoRepository.saveAll(partidos);
    }

    private void generarBracket(Torneo torneo, Categoria categoria, List<Pareja> parejas) {
        List<Pareja> cabezas = parejas.stream()
                .filter(Pareja::isEsCabezaDeSerie)
                .collect(Collectors.toCollection(ArrayList::new));
        List<Pareja> resto = parejas.stream()
                .filter(p -> !p.isEsCabezaDeSerie())
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(resto);

        List<Pareja> seeds = new ArrayList<>(cabezas);
        seeds.addAll(resto);

        int tamano = 1;
        while (tamano < seeds.size())
            tamano *= 2;

        RondaEliminatorias primeraRonda = RondaEliminatorias.builder()
                .nombre(nombreRonda(tamano))
                .orden(1)
                .torneo(torneo)
                .categoria(categoria)
                .build();
        rondaEliminatoriasRepository.save(primeraRonda);

        List<BracketSeeder.Match> llave = BracketSeeder.sembrar(seeds);
        partidoRepository.saveAll(BracketSeeder.construirPartidos(llave, torneo, primeraRonda));
    }

    private String nombreRonda(int tamano) {
        return switch (tamano) {
            case 64 -> "Treintaidosavos de final";
            case 32 -> "Dieciseisavos de final";
            case 16 -> "Octavos de final";
            case 8 -> "Cuartos de final";
            case 4 -> "Semifinales";
            case 2 -> "Final";
            default -> "Eliminación";
        };
    }

    private List<Partido> generarPartidosRoundRobin(List<Pareja> parejas, Grupo grupo, Torneo torneo) {
        List<Partido> partidos = new ArrayList<>();
        if (parejas.size() < 2) {
            return partidos;
        }

        List<Pareja> rueda = new ArrayList<>(parejas);
        if (rueda.size() % 2 != 0) {
            rueda.add(null);
        }
        int n = rueda.size();
        int jornadas = n - 1;
        int partidosPorJornada = n / 2;

        for (int j = 0; j < jornadas; j++) {
            for (int i = 0; i < partidosPorJornada; i++) {
                Pareja local = rueda.get(i);
                Pareja visitante = rueda.get(n - 1 - i);
                if (local != null && visitante != null) {
                    partidos.add(Partido.builder()
                            .torneo(torneo)
                            .local(local)
                            .visitante(visitante)
                            .grupo(grupo)
                            .estado(EstadoPartido.PENDIENTE)
                            .fase(FasePartido.GRUPOS)
                            .jornada(j + 1)
                            .build());
                }
            }
            rueda.add(1, rueda.remove(n - 1));
        }
        return partidos;
    }
}
