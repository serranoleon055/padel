package com.padel.rankpadel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.TorneoRequest;
import com.padel.rankpadel.dto.response.GrupoResponse;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.dto.response.PosicionGrupoResponse;
import com.padel.rankpadel.dto.response.TorneoResponse;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.ConfiguracionPuntos;
import com.padel.rankpadel.entity.Grupo;
import com.padel.rankpadel.entity.Lugar;
import com.padel.rankpadel.entity.PlantillaFormato;
import com.padel.rankpadel.entity.PlantillaPuntos;
import com.padel.rankpadel.entity.PosicionGrupo;
import com.padel.rankpadel.entity.Temporada;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.enums.EstadoTorneo;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.TorneoMapper;
import com.padel.rankpadel.repository.CategoriaRepository;
import com.padel.rankpadel.repository.ConfiguracionPuntosRepository;
import com.padel.rankpadel.repository.GrupoRepository;
import com.padel.rankpadel.repository.LugarRepository;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.PlantillaFormatoRepository;
import com.padel.rankpadel.repository.PlantillaPuntosRepository;
import com.padel.rankpadel.repository.PosicionGrupoRepository;
import com.padel.rankpadel.repository.TemporadaRepository;
import com.padel.rankpadel.repository.TorneoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class TorneoService {

    private final TorneoRepository torneoRepository;
    private final TorneoMapper torneoMapper;
    private final TemporadaRepository temporadaRepository;
    private final LugarRepository lugarRepository;
    private final CategoriaRepository categoriaRepository;
    private final ConfiguracionPuntosRepository configuracionPuntosRepository;
    private final PlantillaFormatoRepository plantillaFormatoRepository;
    private final PlantillaPuntosRepository plantillaPuntosRepository;
    private final ParejaRepository parejaRepository;
    private final PartidoRepository partidoRepository;
    private final GrupoRepository grupoRepository;
    private final PosicionGrupoRepository posicionGrupoRepository;
    private final RankingService rankingService;
    private final ImageStorageService imageStorageService;

    private static final Map<EstadoTorneo, List<EstadoTorneo>> TRANSICIONES = Map.of(
            EstadoTorneo.BORRADOR, List.of(EstadoTorneo.INSCRIPCION, EstadoTorneo.CANCELADO),
            EstadoTorneo.INSCRIPCION, List.of(EstadoTorneo.BORRADOR, EstadoTorneo.CANCELADO),
            EstadoTorneo.SORTEADO, List.of(EstadoTorneo.EN_CURSO, EstadoTorneo.CANCELADO),
            EstadoTorneo.EN_CURSO, List.of(EstadoTorneo.FINALIZADO),
            EstadoTorneo.FINALIZADO, List.of(EstadoTorneo.EN_CURSO),
            EstadoTorneo.CANCELADO, List.of());

    @Transactional(readOnly = true)
    public List<TorneoResponse> listarTodos() {
        return torneoRepository.findAllConRelaciones()
                .stream()
                .map(this::mapearConMetricas)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TorneoResponse buscarPorId(Long id) {
        Torneo torneo = torneoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", id));
        if (!torneo.isActivo()) {
            throw new ResourceNotFoundException("Torneo", id);
        }
        return mapearConMetricas(torneo);
    }

    public TorneoResponse mapearConMetricas(Torneo torneo) {
        TorneoResponse response = torneoMapper.torneoToResponse(torneo);
        response.setCantidadParejas(parejaRepository.countByTorneoId(torneo.getId()));
        response.setCantidadPartidos(partidoRepository.countByTorneoId(torneo.getId()));
        response.setPartidosFinalizados(
                partidoRepository.countByTorneoIdAndEstado(torneo.getId(), EstadoPartido.FINALIZADO));
        return response;
    }

    @Transactional
    public TorneoResponse crear(TorneoRequest torneoRequest) {
        Temporada temporada = null;
        if (torneoRequest.getTemporadaId() != null) {
            temporada = temporadaRepository.findById(torneoRequest.getTemporadaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Temporada", torneoRequest.getTemporadaId()));
            validarTemporadaActiva(temporada);
        }

        Lugar lugar = null;
        if (torneoRequest.getLugarId() != null) {
            lugar = lugarRepository.findById(torneoRequest.getLugarId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lugar", torneoRequest.getLugarId()));
        }

        Torneo torneo = torneoMapper.requestToTorneo(torneoRequest, temporada, lugar);
        aplicarPlantillaFormatoSiCorresponde(torneo, torneoRequest.getPlantillaFormatoId());
        torneo.setEstado(EstadoTorneo.BORRADOR);

        if (torneoRequest.getCategoriaIds() != null && !torneoRequest.getCategoriaIds().isEmpty()) {
            List<Categoria> categorias = categoriaRepository.findAllById(torneoRequest.getCategoriaIds());
            torneo.getCategorias().addAll(categorias);
        }

        torneoRepository.save(torneo);

        copiarConfiguracionPuntos(torneo, torneoRequest);

        return mapearConMetricas(torneo);
    }

    @Transactional
    public TorneoResponse actualizar(Long id, TorneoRequest torneoRequest) {
        Torneo torneoExistente = torneoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", id));

        if (!torneoExistente.getEstado().equals(EstadoTorneo.BORRADOR)) {
            throw new EstadoInvalidoException("Solo se puede editar un torneo en estado BORRADOR");
        }

        torneoExistente.setNombre(torneoRequest.getNombre());
        torneoExistente.setFormato(torneoRequest.getFormato());
        torneoExistente.setFechaInicio(torneoRequest.getFechaInicio());
        torneoExistente.setFechaFin(torneoRequest.getFechaFin());
        torneoExistente.setEsMixto(torneoRequest.isEsMixto());
        torneoExistente.setSumaPuntosRanking(torneoRequest.isSumaPuntosRanking());
        torneoExistente.setCantidadParejasObjetivo(torneoRequest.getCantidadParejasObjetivo());
        torneoExistente.setCantidadGrupos(torneoRequest.getCantidadGrupos());
        torneoExistente.setParejasPorGrupo(torneoRequest.getParejasPorGrupo());
        torneoExistente.setAvanzanPorGrupo(torneoRequest.getAvanzanPorGrupo());
        torneoExistente.setIncluyeFaseGrupos(torneoRequest.isIncluyeFaseGrupos());
        torneoExistente.setIncluyeEliminacion(torneoRequest.isIncluyeEliminacion());
        torneoExistente.setMejorDeSets(torneoRequest.getMejorDeSets() != null ? torneoRequest.getMejorDeSets() : 3);
        torneoExistente.setTipoSorteo(torneoRequest.getTipoSorteo());
        torneoExistente.setCupoMaximoParejas(torneoRequest.getCupoMaximoParejas());
        torneoExistente.setCuposPorCategoria(torneoRequest.getCuposPorCategoria() != null
                ? new java.util.HashMap<>(torneoRequest.getCuposPorCategoria())
                : new java.util.HashMap<>());
        aplicarPlantillaFormatoSiCorresponde(torneoExistente, torneoRequest.getPlantillaFormatoId());

        if (torneoRequest.getLugarId() != null) {
            Lugar lugar = lugarRepository.findById(torneoRequest.getLugarId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lugar", torneoRequest.getLugarId()));
            torneoExistente.setLugar(lugar);
        }
        if (torneoRequest.getTemporadaId() != null) {
            Temporada temporada = temporadaRepository.findById(torneoRequest.getTemporadaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Temporada", torneoRequest.getTemporadaId()));
            validarTemporadaActiva(temporada);
            torneoExistente.setTemporada(temporada);
        } else {
            torneoExistente.setTemporada(null);
        }

        if (torneoRequest.getConfiguracionPuntos() != null || torneoRequest.getPlantillaPuntosId() != null) {
            configuracionPuntosRepository.deleteAll(configuracionPuntosRepository.findByTorneoIdOrderByOrden(id));
            copiarConfiguracionPuntos(torneoExistente, torneoRequest);
        }

        torneoRepository.save(torneoExistente);
        return mapearConMetricas(torneoExistente);
    }

    @Transactional
    public void eliminar(Long id) {
        Torneo torneo = torneoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", id));

        List<Partido> partidos = partidoRepository.findByTorneoId(id);
        List<Pareja> parejas = parejaRepository.findByTorneoId(id);
        rankingService.revertirRankingTorneo(torneo, partidos, parejas);

        torneo.setActivo(false);
        torneoRepository.save(torneo);
    }

    @Transactional
    public TorneoResponse cambiarEstado(Long torneoId, EstadoTorneo nuevoEstado) {
        Torneo torneo = torneoRepository.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", torneoId));

        EstadoTorneo estadoAnterior = torneo.getEstado();
        validarTransicion(estadoAnterior, nuevoEstado);

        if (nuevoEstado.equals(EstadoTorneo.EN_CURSO)) {
            long totalPartidos = partidoRepository.countByTorneoId(torneoId);
            if (totalPartidos == 0) {
                throw new EstadoInvalidoException(
                        "No se puede iniciar un torneo sin partidos. Generá el sorteo primero.");
            }
        }

        if (estadoAnterior.equals(EstadoTorneo.FINALIZADO) && nuevoEstado.equals(EstadoTorneo.EN_CURSO)) {
            rankingService.reabrirTorneo(torneoId);
        }

        torneo.setEstado(nuevoEstado);
        torneoRepository.save(torneo);

        return mapearConMetricas(torneo);
    }

    private void validarTransicion(EstadoTorneo actual, EstadoTorneo nuevo) {
        List<EstadoTorneo> permitidos = TRANSICIONES.getOrDefault(actual, List.of());
        if (!permitidos.contains(nuevo)) {
            throw new EstadoInvalidoException("No se puede pasar de " + actual + " a " + nuevo);
        }
    }

    private void aplicarPlantillaFormatoSiCorresponde(Torneo torneo, Long plantillaFormatoId) {
        if (plantillaFormatoId == null) {
            torneo.setPlantillaFormatoId(null);
            torneo.setPlantillaFormatoNombre(null);
            return;
        }

        PlantillaFormato plantilla = plantillaFormatoRepository.findByIdAndActivoTrue(plantillaFormatoId)
                .orElseThrow(() -> new ResourceNotFoundException("PlantillaFormato", plantillaFormatoId));

        torneo.setPlantillaFormatoId(plantilla.getId());
        torneo.setPlantillaFormatoNombre(plantilla.getNombre());
        if (torneo.getFormato() == null) {
            torneo.setFormato(plantilla.getFormatoTorneo());
        }
        torneo.setTipoSorteo(plantilla.getTipoSorteo());
        torneo.setCantidadParejasObjetivo(plantilla.getCantidadParejasObjetivo());
        torneo.setCantidadGrupos(plantilla.getCantidadGrupos());
        torneo.setParejasPorGrupo(plantilla.getParejasPorGrupo());
        torneo.setAvanzanPorGrupo(plantilla.getAvanzanPorGrupo());
        if (torneo.getFormato() != null && torneo.getFormato().equals(com.padel.rankpadel.enums.FormatoTorneo.LIGA)) {
            torneo.setIncluyeFaseGrupos(true);
            torneo.setIncluyeEliminacion(false);
        } else {
            torneo.setIncluyeFaseGrupos(plantilla.isIncluyeFaseGrupos());
            torneo.setIncluyeEliminacion(plantilla.isIncluyeEliminacion());
        }
    }

    private void copiarConfiguracionPuntos(Torneo torneo, TorneoRequest torneoRequest) {
        PlantillaPuntos plantilla = null;
        if (torneoRequest.getPlantillaPuntosId() != null) {
            plantilla = plantillaPuntosRepository.findByIdAndActivoTrue(torneoRequest.getPlantillaPuntosId())
                    .orElseThrow(() -> new ResourceNotFoundException("PlantillaPuntos", torneoRequest.getPlantillaPuntosId()));
            torneo.setPlantillaPuntosId(plantilla.getId());
            torneo.setPlantillaPuntosNombre(plantilla.getNombre());
        } else {
            torneo.setPlantillaPuntosId(null);
            torneo.setPlantillaPuntosNombre(null);
        }

        List<ConfiguracionPuntos> configs;
        if (plantilla != null) {
            configs = plantilla.getRondas().stream()
                    .map(ronda -> ConfiguracionPuntos.builder()
                            .nombreRonda(ronda.getNombreRonda())
                            .puntosGanador(ronda.getPuntosGanador())
                            .puntosPerdedor(ronda.getPuntosPerdedor())
                            .orden(ronda.getOrden())
                            .torneo(torneo)
                            .build())
                    .collect(Collectors.toList());
        } else if (torneoRequest.getConfiguracionPuntos() != null && !torneoRequest.getConfiguracionPuntos().isEmpty()) {
            configs = torneoRequest.getConfiguracionPuntos().stream()
                    .map(cp -> ConfiguracionPuntos.builder()
                            .nombreRonda(cp.getNombreRonda())
                            .puntosGanador(cp.getPuntosGanador())
                            .puntosPerdedor(cp.getPuntosPerdedor())
                            .orden(cp.getOrden())
                            .torneo(torneo)
                            .build())
                    .collect(Collectors.toList());
        } else {
            configs = List.of();
        }

        if (!configs.isEmpty()) {
            configuracionPuntosRepository.saveAll(configs);
            torneo.setConfiguracionPuntos(configs);
        } else {
            torneo.setConfiguracionPuntos(List.of());
        }
    }

    private void validarTemporadaActiva(Temporada temporada) {
        if (!temporada.isActiva()) {
            throw new EstadoInvalidoException("Solo se pueden asignar temporadas activas a un torneo");
        }
    }

    public TorneoResponse actualizarImagen(Long id, org.springframework.web.multipart.MultipartFile file) {
        Torneo torneo = torneoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", id));
        imageStorageService.borrarPorUrl(torneo.getImagenUrl());
        torneo.setImagenUrl(imageStorageService.guardarTorneoImagen(id, file));
        torneoRepository.save(torneo);
        return torneoMapper.torneoToResponse(torneo);
    }

    @Transactional(readOnly = true)
    public List<GrupoResponse> listarGruposConPosiciones(Long torneoId) {
        torneoRepository.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", torneoId));

        List<Grupo> grupos = grupoRepository.findByTorneoId(torneoId);
        List<GrupoResponse> responses = new ArrayList<>();

        for (Grupo grupo : grupos) {
            List<PosicionGrupo> posiciones = com.padel.rankpadel.util.PosicionGrupoOrdenador.ordenar(
                    posicionGrupoRepository.findByGrupoId(grupo.getId()));
            List<PosicionGrupoResponse> posResponses = new ArrayList<>();
            int pos = 1;
            for (PosicionGrupo pg : posiciones) {
                String parejaNombre = "";
                if (pg.getPareja() != null && pg.getPareja().getJugador1() != null
                        && pg.getPareja().getJugador2() != null) {
                    parejaNombre = pg.getPareja().getJugador1().getNombre() + " "
                            + pg.getPareja().getJugador1().getApellido()
                            + " / " + pg.getPareja().getJugador2().getNombre() + " "
                            + pg.getPareja().getJugador2().getApellido();
                }
                posResponses.add(PosicionGrupoResponse.builder()
                        .id(pg.getId())
                        .posicion(pos++)
                        .pj(pg.getPj())
                        .pg(pg.getPg())
                        .pp(pg.getPp())
                        .puntos(pg.getPuntos())
                        .setsGanados(pg.getSetsGanados())
                        .setsPerdidos(pg.getSetsPerdidos())
                        .juegosGanados(pg.getJuegosGanados())
                        .juegosPerdidos(pg.getJuegosPerdidos())
                        .parejaId(pg.getPareja().getId())
                        .parejaNombre(parejaNombre)
                        .build());
            }
            responses.add(GrupoResponse.builder()
                    .id(grupo.getId())
                    .nombre(grupo.getNombre())
                    .categoriaId(grupo.getCategoria().getId())
                    .categoriaNombre(grupo.getCategoria().getNombre())
                    .posiciones(posResponses)
                    .build());
        }

        return responses;
    }
}
