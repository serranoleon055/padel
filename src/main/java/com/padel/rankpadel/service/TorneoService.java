package com.padel.rankpadel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.ConfiguracionCategoriaTorneoRequest;
import com.padel.rankpadel.dto.request.ConfiguracionPuntosRequest;
import com.padel.rankpadel.dto.request.TorneoRequest;
import com.padel.rankpadel.dto.response.GrupoResponse;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.dto.response.PosicionGrupoResponse;
import com.padel.rankpadel.dto.response.TorneoResponse;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.ConfiguracionCategoriaTorneo;
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
import com.padel.rankpadel.enums.FormatoTorneo;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.TorneoMapper;
import com.padel.rankpadel.repository.CategoriaRepository;
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
    private final PlantillaFormatoRepository plantillaFormatoRepository;
    private final PlantillaPuntosRepository plantillaPuntosRepository;
    private final ParejaRepository parejaRepository;
    private final PartidoRepository partidoRepository;
    private final GrupoRepository grupoRepository;
    private final PosicionGrupoRepository posicionGrupoRepository;
    private final RankingService rankingService;
    private final CampeonService campeonService;
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
        Map<Long, Long> parejasPorTorneo = aMapaConteo(parejaRepository.contarPorTorneo());
        Map<Long, Long> partidosPorTorneo = aMapaConteo(partidoRepository.contarPorTorneo());
        Map<Long, Long> finalizadosPorTorneo = aMapaConteo(partidoRepository.contarPorTorneoYEstado(EstadoPartido.FINALIZADO));

        return torneoRepository.findAllConRelaciones()
                .stream()
                .map(torneo -> {
                    TorneoResponse response = torneoMapper.torneoToResponse(torneo);
                    response.setCantidadParejas(parejasPorTorneo.getOrDefault(torneo.getId(), 0L));
                    response.setCantidadPartidos(partidosPorTorneo.getOrDefault(torneo.getId(), 0L));
                    response.setPartidosFinalizados(finalizadosPorTorneo.getOrDefault(torneo.getId(), 0L));
                    return response;
                })
                .collect(Collectors.toList());
    }

    private Map<Long, Long> aMapaConteo(List<Object[]> filas) {
        Map<Long, Long> mapa = new java.util.HashMap<>();
        for (Object[] fila : filas) {
            mapa.put((Long) fila[0], (Long) fila[1]);
        }
        return mapa;
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
        if (torneoRequest.getTemporadaId() == null) {
            throw new EstadoInvalidoException("El torneo debe tener una temporada asignada");
        }
        Temporada temporada = temporadaRepository.findById(torneoRequest.getTemporadaId())
                .orElseThrow(() -> new ResourceNotFoundException("Temporada", torneoRequest.getTemporadaId()));
        validarTemporadaActiva(temporada);

        if (torneoRequest.getLugarId() == null) {
            throw new EstadoInvalidoException("El torneo debe tener un lugar asignado");
        }
        Lugar lugar = lugarRepository.findById(torneoRequest.getLugarId())
                .orElseThrow(() -> new ResourceNotFoundException("Lugar", torneoRequest.getLugarId()));

        Torneo torneo = torneoMapper.requestToTorneo(torneoRequest, temporada, lugar);
        aplicarPlantillaFormatoSiCorresponde(torneo, torneoRequest.getPlantillaFormatoId());
        torneo.setEstado(EstadoTorneo.BORRADOR);

        if (torneoRequest.getCategoriaIds() != null && !torneoRequest.getCategoriaIds().isEmpty()) {
            List<Categoria> categorias = categoriaRepository.findAllById(torneoRequest.getCategoriaIds());
            torneo.getCategorias().addAll(categorias);
        }

        torneoRepository.save(torneo);

        aplicarPlantillaPuntosTorneoMeta(torneo, torneoRequest.getPlantillaPuntosId());
        sincronizarConfiguracionPorCategoria(torneo, torneoRequest);

        return mapearConMetricas(torneo);
    }

    @Transactional
    public TorneoResponse actualizar(Long id, TorneoRequest torneoRequest) {
        Torneo torneoExistente = torneoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", id));

        torneoExistente.setNombre(torneoRequest.getNombre());
        torneoExistente.setDescripcion(torneoRequest.getDescripcion());
        torneoExistente.setImagenUrl(torneoRequest.getImagenUrl());
        torneoExistente.setCostoInscripcionJugador(torneoRequest.getCostoInscripcionJugador());
        torneoExistente.setPremioAcumulado(torneoRequest.getPremioAcumulado());
        torneoExistente.setSeniaPorcentaje(torneoRequest.getSeniaPorcentaje());
        torneoExistente.setFechaInicio(torneoRequest.getFechaInicio());
        torneoExistente.setFechaFin(torneoRequest.getFechaFin());

        if (torneoRequest.getLugarId() == null) {
            throw new EstadoInvalidoException("El torneo debe tener un lugar asignado");
        }
        Lugar lugar = lugarRepository.findById(torneoRequest.getLugarId())
                .orElseThrow(() -> new ResourceNotFoundException("Lugar", torneoRequest.getLugarId()));
        torneoExistente.setLugar(lugar);
        if (torneoRequest.getTemporadaId() != null) {
            Temporada temporada = temporadaRepository.findById(torneoRequest.getTemporadaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Temporada", torneoRequest.getTemporadaId()));
            boolean cambiaTemporada = torneoExistente.getTemporada() == null
                    || !temporada.getId().equals(torneoExistente.getTemporada().getId());
            if (cambiaTemporada) {
                validarTemporadaActiva(temporada);
            }
            torneoExistente.setTemporada(temporada);
        } else {
            torneoExistente.setTemporada(null);
        }

        if (permiteEditarConfiguracion(torneoExistente.getEstado())) {
            torneoExistente.setFormato(torneoRequest.getFormato());
            torneoExistente.setSumaPuntosRanking(torneoRequest.isSumaPuntosRanking());
            torneoExistente.setCantidadParejasObjetivo(torneoRequest.getCantidadParejasObjetivo());
            torneoExistente.setCantidadGrupos(torneoRequest.getCantidadGrupos());
            torneoExistente.setParejasPorGrupo(torneoRequest.getParejasPorGrupo());
            torneoExistente.setAvanzanPorGrupo(torneoRequest.getAvanzanPorGrupo());
            torneoExistente.setIncluyeFaseGrupos(torneoRequest.isIncluyeFaseGrupos());
            torneoExistente.setIncluyeEliminacion(torneoRequest.isIncluyeEliminacion());
            torneoExistente.setMejorDeSets(TorneoMapper.mejorDeSetsPorDefecto(torneoRequest.getMejorDeSets(), torneoRequest.getFormato()));
            torneoExistente.setTipoSorteo(torneoRequest.getTipoSorteo());
            torneoExistente.setCupoMaximoParejas(torneoRequest.getCupoMaximoParejas());
            torneoExistente.setCuposPorCategoria(torneoRequest.getCuposPorCategoria() != null
                    ? new java.util.HashMap<>(torneoRequest.getCuposPorCategoria())
                    : new java.util.HashMap<>());
            aplicarPlantillaFormatoSiCorresponde(torneoExistente, torneoRequest.getPlantillaFormatoId());

            aplicarPlantillaPuntosTorneoMeta(torneoExistente, torneoRequest.getPlantillaPuntosId());
            sincronizarConfiguracionPorCategoria(torneoExistente, torneoRequest);
        }

        torneoRepository.save(torneoExistente);
        return mapearConMetricas(torneoExistente);
    }

    @Transactional
    public TorneoResponse reaplicarPlantillaPuntos(Long id, Long categoriaId, Long plantillaPuntosId) {
        Torneo torneo = torneoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", id));

        List<ConfiguracionCategoriaTorneo> objetivos = torneo.getConfiguracionesCategoria().stream()
                .filter(config -> categoriaId == null || categoriaId.equals(idCategoria(config)))
                .collect(Collectors.toList());

        if (objetivos.isEmpty()) {
            throw new EstadoInvalidoException("El torneo no tiene configuración de puntos por categoría para reaplicar.");
        }

        boolean algunaReaplicada = false;
        for (ConfiguracionCategoriaTorneo config : objetivos) {
            Long idPlantilla = plantillaPuntosId != null ? plantillaPuntosId : config.getPlantillaPuntosId();
            if (idPlantilla == null) {
                continue;
            }
            PlantillaPuntos plantilla = plantillaPuntosRepository.findByIdAndActivoTrue(idPlantilla)
                    .orElseThrow(() -> new ResourceNotFoundException("PlantillaPuntos", idPlantilla));
            config.setPlantillaPuntosId(plantilla.getId());
            config.setPlantillaPuntosNombre(plantilla.getNombre());
            reemplazarPuntosDeCategoria(torneo, config.getCategoria(), plantilla);
            algunaReaplicada = true;
        }

        if (!algunaReaplicada) {
            throw new EstadoInvalidoException("Ninguna categoría tiene una plantilla de puntos para reaplicar. Elegí una.");
        }

        torneoRepository.save(torneo);

        rankingService.recalcularPuntos();

        return mapearConMetricas(torneo);
    }

    private void reemplazarPuntosDeCategoria(Torneo torneo, Categoria categoria, PlantillaPuntos plantilla) {
        if (torneo.getConfiguracionPuntos() == null) {
            torneo.setConfiguracionPuntos(new ArrayList<>());
        }
        Long categoriaId = categoria.getId();
        torneo.getConfiguracionPuntos().removeIf(cp -> categoriaId.equals(idCategoria(cp)));
        plantilla.getRondas().forEach(ronda -> torneo.getConfiguracionPuntos().add(ConfiguracionPuntos.builder()
                .nombreRonda(ronda.getNombreRonda())
                .puntosGanador(ronda.getPuntosGanador())
                .puntosPerdedor(ronda.getPuntosPerdedor())
                .orden(ronda.getOrden())
                .torneo(torneo)
                .categoria(categoria)
                .build()));
    }

    private Long idCategoria(ConfiguracionCategoriaTorneo config) {
        try {
            return config.getCategoria() != null ? config.getCategoria().getId() : null;
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return null;
        }
    }

    private Long idCategoria(ConfiguracionPuntos puntos) {
        try {
            return puntos.getCategoria() != null ? puntos.getCategoria().getId() : null;
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return null;
        }
    }

    private void reemplazarConfiguracionPuntos(Torneo torneo, List<ConfiguracionPuntos> configs) {
        if (torneo.getConfiguracionPuntos() == null) {
            torneo.setConfiguracionPuntos(new ArrayList<>());
        } else {
            torneo.getConfiguracionPuntos().clear();
        }
        torneo.getConfiguracionPuntos().addAll(configs);
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
            campeonService.eliminarPorTorneo(torneoId);
        }

        torneo.setEstado(nuevoEstado);
        torneoRepository.save(torneo);

        if (nuevoEstado.equals(EstadoTorneo.FINALIZADO)) {
            campeonService.recalcularCampeones(torneo);
        }

        return mapearConMetricas(torneo);
    }

    private boolean permiteEditarConfiguracion(EstadoTorneo estado) {
        return estado == EstadoTorneo.BORRADOR || estado == EstadoTorneo.INSCRIPCION;
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

    private void aplicarPlantillaPuntosTorneoMeta(Torneo torneo, Long plantillaPuntosId) {
        if (plantillaPuntosId == null) {
            torneo.setPlantillaPuntosId(null);
            torneo.setPlantillaPuntosNombre(null);
            return;
        }
        PlantillaPuntos plantilla = plantillaPuntosRepository.findByIdAndActivoTrue(plantillaPuntosId)
                .orElseThrow(() -> new ResourceNotFoundException("PlantillaPuntos", plantillaPuntosId));
        torneo.setPlantillaPuntosId(plantilla.getId());
        torneo.setPlantillaPuntosNombre(plantilla.getNombre());
    }

    private void sincronizarConfiguracionPorCategoria(Torneo torneo, TorneoRequest request) {
        Map<Long, ConfiguracionCategoriaTorneoRequest> porCategoria = new java.util.HashMap<>();
        if (request.getConfiguracionesCategoria() != null) {
            for (ConfiguracionCategoriaTorneoRequest config : request.getConfiguracionesCategoria()) {
                if (config.getCategoriaId() != null) {
                    porCategoria.put(config.getCategoriaId(), config);
                }
            }
        }

        List<ConfiguracionCategoriaTorneo> configs = new ArrayList<>();
        List<ConfiguracionPuntos> puntos = new ArrayList<>();

        for (Categoria categoria : torneo.getCategorias()) {
            ConfiguracionCategoriaTorneoRequest req = porCategoria.get(categoria.getId());
            configs.add(construirConfigCategoria(torneo, categoria, req, request));
            puntos.addAll(construirPuntosCategoria(torneo, categoria, req, request));
        }

        reemplazarConfiguracionesCategoria(torneo, configs);
        reemplazarConfiguracionPuntos(torneo, puntos);
    }

    private ConfiguracionCategoriaTorneo construirConfigCategoria(Torneo torneo, Categoria categoria,
            ConfiguracionCategoriaTorneoRequest req, TorneoRequest base) {
        ConfiguracionCategoriaTorneo config = ConfiguracionCategoriaTorneo.builder()
                .torneo(torneo)
                .categoria(categoria)
                .build();

        if (req != null) {
            FormatoTorneo formato = req.getFormato() != null ? req.getFormato() : base.getFormato();
            config.setFormato(formato);
            config.setTipoSorteo(req.getTipoSorteo() != null ? req.getTipoSorteo() : base.getTipoSorteo());
            config.setCantidadParejasObjetivo(req.getCantidadParejasObjetivo());
            config.setCantidadGrupos(req.getCantidadGrupos());
            config.setParejasPorGrupo(req.getParejasPorGrupo());
            config.setAvanzanPorGrupo(req.getAvanzanPorGrupo());
            config.setIncluyeFaseGrupos(req.isIncluyeFaseGrupos());
            config.setIncluyeEliminacion(req.isIncluyeEliminacion());
            config.setMejorDeSets(TorneoMapper.mejorDeSetsPorDefecto(req.getMejorDeSets(), formato));
            config.setCupo(req.getCantidadParejasObjetivo());
            resolverMetaPlantillaFormato(config, req.getPlantillaFormatoId());
            aplicarPlantillaPuntosMetaAConfig(config, req.getPlantillaPuntosId());
            if (formato == FormatoTorneo.LIGA) {
                config.setIncluyeFaseGrupos(true);
                config.setIncluyeEliminacion(false);
            }
            return config;
        }

        config.setFormato(base.getFormato());
        config.setTipoSorteo(base.getTipoSorteo());
        config.setCantidadParejasObjetivo(base.getCantidadParejasObjetivo());
        config.setCantidadGrupos(base.getCantidadGrupos());
        config.setParejasPorGrupo(base.getParejasPorGrupo());
        config.setAvanzanPorGrupo(base.getAvanzanPorGrupo());
        config.setIncluyeFaseGrupos(base.isIncluyeFaseGrupos());
        config.setIncluyeEliminacion(base.isIncluyeEliminacion());
        config.setMejorDeSets(TorneoMapper.mejorDeSetsPorDefecto(base.getMejorDeSets(), base.getFormato()));
        config.setCupo(base.getCuposPorCategoria() != null ? base.getCuposPorCategoria().get(categoria.getId()) : null);
        aplicarPlantillaFormatoAConfig(config, base.getPlantillaFormatoId());
        aplicarPlantillaPuntosMetaAConfig(config, base.getPlantillaPuntosId());
        if (config.getFormato() == FormatoTorneo.LIGA) {
            config.setIncluyeFaseGrupos(true);
            config.setIncluyeEliminacion(false);
        }
        return config;
    }

    private void resolverMetaPlantillaFormato(ConfiguracionCategoriaTorneo config, Long plantillaFormatoId) {
        if (plantillaFormatoId == null) {
            config.setPlantillaFormatoId(null);
            config.setPlantillaFormatoNombre(null);
            return;
        }
        PlantillaFormato plantilla = plantillaFormatoRepository.findByIdAndActivoTrue(plantillaFormatoId)
                .orElseThrow(() -> new ResourceNotFoundException("PlantillaFormato", plantillaFormatoId));
        config.setPlantillaFormatoId(plantilla.getId());
        config.setPlantillaFormatoNombre(plantilla.getNombre());
    }

    private List<ConfiguracionPuntos> construirPuntosCategoria(Torneo torneo, Categoria categoria,
            ConfiguracionCategoriaTorneoRequest req, TorneoRequest base) {
        Long plantillaPuntosId = req != null ? req.getPlantillaPuntosId() : base.getPlantillaPuntosId();
        List<ConfiguracionPuntosRequest> inline = req != null ? req.getConfiguracionPuntos() : base.getConfiguracionPuntos();

        if (plantillaPuntosId != null) {
            PlantillaPuntos plantilla = plantillaPuntosRepository.findByIdAndActivoTrue(plantillaPuntosId)
                    .orElseThrow(() -> new ResourceNotFoundException("PlantillaPuntos", plantillaPuntosId));
            return plantilla.getRondas().stream()
                    .map(ronda -> ConfiguracionPuntos.builder()
                            .nombreRonda(ronda.getNombreRonda())
                            .puntosGanador(ronda.getPuntosGanador())
                            .puntosPerdedor(ronda.getPuntosPerdedor())
                            .orden(ronda.getOrden())
                            .torneo(torneo)
                            .categoria(categoria)
                            .build())
                    .collect(Collectors.toList());
        }
        if (inline != null && !inline.isEmpty()) {
            return inline.stream()
                    .map(cp -> ConfiguracionPuntos.builder()
                            .nombreRonda(cp.getNombreRonda())
                            .puntosGanador(cp.getPuntosGanador())
                            .puntosPerdedor(cp.getPuntosPerdedor())
                            .orden(cp.getOrden())
                            .torneo(torneo)
                            .categoria(categoria)
                            .build())
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private void aplicarPlantillaFormatoAConfig(ConfiguracionCategoriaTorneo config, Long plantillaFormatoId) {
        if (plantillaFormatoId == null) {
            config.setPlantillaFormatoId(null);
            config.setPlantillaFormatoNombre(null);
            return;
        }
        PlantillaFormato plantilla = plantillaFormatoRepository.findByIdAndActivoTrue(plantillaFormatoId)
                .orElseThrow(() -> new ResourceNotFoundException("PlantillaFormato", plantillaFormatoId));
        config.setPlantillaFormatoId(plantilla.getId());
        config.setPlantillaFormatoNombre(plantilla.getNombre());
        if (config.getFormato() == null) {
            config.setFormato(plantilla.getFormatoTorneo());
        }
        config.setTipoSorteo(plantilla.getTipoSorteo());
        config.setCantidadParejasObjetivo(plantilla.getCantidadParejasObjetivo());
        config.setCantidadGrupos(plantilla.getCantidadGrupos());
        config.setParejasPorGrupo(plantilla.getParejasPorGrupo());
        config.setAvanzanPorGrupo(plantilla.getAvanzanPorGrupo());
        if (config.getFormato() == FormatoTorneo.LIGA) {
            config.setIncluyeFaseGrupos(true);
            config.setIncluyeEliminacion(false);
        } else {
            config.setIncluyeFaseGrupos(plantilla.isIncluyeFaseGrupos());
            config.setIncluyeEliminacion(plantilla.isIncluyeEliminacion());
        }
    }

    private void aplicarPlantillaPuntosMetaAConfig(ConfiguracionCategoriaTorneo config, Long plantillaPuntosId) {
        if (plantillaPuntosId == null) {
            config.setPlantillaPuntosId(null);
            config.setPlantillaPuntosNombre(null);
            return;
        }
        PlantillaPuntos plantilla = plantillaPuntosRepository.findByIdAndActivoTrue(plantillaPuntosId)
                .orElseThrow(() -> new ResourceNotFoundException("PlantillaPuntos", plantillaPuntosId));
        config.setPlantillaPuntosId(plantilla.getId());
        config.setPlantillaPuntosNombre(plantilla.getNombre());
    }

    private void reemplazarConfiguracionesCategoria(Torneo torneo, List<ConfiguracionCategoriaTorneo> configs) {
        if (torneo.getConfiguracionesCategoria() == null) {
            torneo.setConfiguracionesCategoria(new ArrayList<>());
        } else {
            torneo.getConfiguracionesCategoria().clear();
        }
        torneo.getConfiguracionesCategoria().addAll(configs);
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
